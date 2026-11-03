package br.com.qsoft.governance.aigateway.infrastructure.query;

import br.com.qsoft.governance.aigateway.api.exception.ModelNotAllowedException;
import br.com.qsoft.governance.aigateway.api.exception.ModelUnavailableException;
import br.com.qsoft.governance.aigateway.api.exception.NoModelConfiguredException;
import br.com.qsoft.governance.aigateway.api.exception.ProviderInactiveException;
import br.com.qsoft.governance.aigateway.api.model.AllowedModel;
import br.com.qsoft.governance.aigateway.api.model.AllowedModels;
import br.com.qsoft.governance.aigateway.api.model.ResolvedModelInfo;
import br.com.qsoft.governance.aigateway.api.query.ModelResolutionQuery;
import br.com.qsoft.governance.aigateway.application.ResolveModelForUser;
import br.com.qsoft.governance.aigateway.application.ResolvedModel;
import br.com.qsoft.governance.aigateway.application.port.ModelRepository;
import br.com.qsoft.governance.aigateway.application.port.ProviderRepository;
import br.com.qsoft.governance.aigateway.domain.exception.ModelNotFoundException;
import br.com.qsoft.governance.aigateway.domain.exception.ProviderNotFoundException;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.aigateway.domain.provider.Provider;
import br.com.qsoft.governance.department.api.model.ModelPolicyInfo;
import br.com.qsoft.governance.department.api.query.DepartmentModelPolicyQuery;
import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Adapter do contrato público sobre o roteamento determinístico existente. */
@Component
public class ModelResolutionQueryAdapter implements ModelResolutionQuery {

    private final ResolveModelForUser resolveModelForUser;
    private final IdentityQuery identityQuery;
    private final DepartmentModelPolicyQuery modelPolicyQuery;
    private final ModelRepository modelRepository;
    private final ProviderRepository providerRepository;

    public ModelResolutionQueryAdapter(ResolveModelForUser resolveModelForUser,
                                       IdentityQuery identityQuery,
                                       DepartmentModelPolicyQuery modelPolicyQuery,
                                       ModelRepository modelRepository,
                                       ProviderRepository providerRepository) {
        this.resolveModelForUser = resolveModelForUser;
        this.identityQuery = identityQuery;
        this.modelPolicyQuery = modelPolicyQuery;
        this.modelRepository = modelRepository;
        this.providerRepository = providerRepository;
    }

    @Override
    public Optional<ResolvedModelInfo> resolve(TenantId tenantId, UserId userId,
                                               UUID requestedModelId) {
        try {
            ResolvedModel model = resolveModelForUser.resolve(tenantId, userId, requestedModelId);
            return Optional.of(new ResolvedModelInfo(model.modelId(), model.modelName(),
                model.providerId(), model.providerName()));
        } catch (ModelNotAllowedException | NoModelConfiguredException
                 | ModelUnavailableException | ProviderInactiveException
                 | ModelNotFoundException | ProviderNotFoundException ex) {
            return Optional.empty();
        }
    }

    @Override
    public AllowedModels allowedModels(TenantId tenantId, UserId userId) {
        UserContext user = identityQuery.getUserContext(userId)
            .filter(context -> context.tenantId().equals(tenantId))
            .orElse(null);
        if (user == null || user.departmentId() == null) {
            return AllowedModels.empty();
        }

        ModelPolicyInfo policy = modelPolicyQuery
            .findByDepartmentId(tenantId, user.departmentId())
            .orElse(null);
        if (policy == null) {
            return AllowedModels.empty();
        }

        List<Model> models = modelRepository
            .findByTenantIdAndIdIn(tenantId, policy.allowedModelIds());
        Map<UUID, Provider> providers = providerRepository.findByTenantId(tenantId).stream()
            .collect(Collectors.toMap(Provider::id, Function.identity()));

        List<AllowedModel> allowed = models.stream()
            .sorted(Comparator.comparing(Model::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Model::id))
            .map(model -> toAllowedModel(model, providers.get(model.providerId())))
            .toList();
        return new AllowedModels(policy.defaultModelId(), allowed);
    }

    private static AllowedModel toAllowedModel(Model model, Provider provider) {
        String unavailableReason = null;
        if (!model.isAvailable()) {
            unavailableReason = "MODEL_UNAVAILABLE";
        } else if (provider == null || !provider.isActive()) {
            unavailableReason = "PROVIDER_INACTIVE";
        }
        return new AllowedModel(
            model.id(),
            model.name(),
            provider == null ? null : provider.name(),
            unavailableReason == null,
            unavailableReason
        );
    }
}
