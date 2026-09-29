package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.aigateway.application.port.ModelRepository;
import br.com.qsoft.governance.aigateway.application.port.ProviderRepository;
import br.com.qsoft.governance.aigateway.domain.exception.ModelNotAllowedException;
import br.com.qsoft.governance.aigateway.domain.exception.ModelNotFoundException;
import br.com.qsoft.governance.aigateway.domain.exception.ModelUnavailableException;
import br.com.qsoft.governance.aigateway.domain.exception.NoModelConfiguredException;
import br.com.qsoft.governance.aigateway.domain.exception.ProviderInactiveException;
import br.com.qsoft.governance.aigateway.domain.exception.ProviderNotFoundException;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.aigateway.domain.provider.Provider;
import br.com.qsoft.governance.department.api.model.ModelPolicyInfo;
import br.com.qsoft.governance.department.api.query.DepartmentModelPolicyQuery;
import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Roteamento determinístico (SPI-83/84): resolve o modelo aplicável a um
 * usuário combinando <b>IAM</b> (identidade/departamento) + <b>ModelPolicy</b>
 * do department + catálogo do aigateway.
 * <p>
 * Regra (ADR-07/08/11): {@code requestedModelId} presente precisa pertencer a
 * {@code allowedModelIds}; ausente usa {@code defaultModelId}. Sempre exige
 * Model AVAILABLE e Provider ACTIVE. <b>Não há fallback, heurística, custo ou
 * latência.</b>
 */
@Service
public class ResolveModelForUser {

    private final IdentityQuery identityQuery;
    private final DepartmentModelPolicyQuery modelPolicyQuery;
    private final ModelRepository modelRepository;
    private final ProviderRepository providerRepository;

    public ResolveModelForUser(IdentityQuery identityQuery,
                               DepartmentModelPolicyQuery modelPolicyQuery,
                               ModelRepository modelRepository,
                               ProviderRepository providerRepository) {
        this.identityQuery = identityQuery;
        this.modelPolicyQuery = modelPolicyQuery;
        this.modelRepository = modelRepository;
        this.providerRepository = providerRepository;
    }

    public ResolvedModel resolve(TenantId tenantId, UserId userId, UUID requestedModelId) {
        UserContext user = identityQuery.getUserContext(userId)
            .filter(context -> context.tenantId().equals(tenantId))
            .orElseThrow(NoModelConfiguredException::new);

        UUID departmentId = user.departmentId();
        if (departmentId == null) {
            throw new NoModelConfiguredException();
        }

        ModelPolicyInfo policy = modelPolicyQuery.findByDepartmentId(tenantId, departmentId)
            .orElseThrow(NoModelConfiguredException::new);

        UUID chosenModelId = chooseModel(policy, requestedModelId);

        Model model = modelRepository.findByTenantIdAndId(tenantId, chosenModelId)
            .orElseThrow(() -> new ModelNotFoundException(chosenModelId));
        if (!model.isAvailable()) {
            throw new ModelUnavailableException();
        }

        Provider provider = providerRepository.findByTenantIdAndId(tenantId, model.providerId())
            .orElseThrow(() -> new ProviderNotFoundException(model.providerId()));
        if (!provider.isActive()) {
            throw new ProviderInactiveException();
        }

        return new ResolvedModel(model.id(), model.name(), provider.id(), provider.name());
    }

    private static UUID chooseModel(ModelPolicyInfo policy, UUID requestedModelId) {
        if (requestedModelId != null) {
            if (!policy.allowedModelIds().contains(requestedModelId)) {
                throw new ModelNotAllowedException();
            }
            return requestedModelId;
        }
        if (policy.defaultModelId() == null) {
            throw new NoModelConfiguredException();
        }
        return policy.defaultModelId();
    }
}
