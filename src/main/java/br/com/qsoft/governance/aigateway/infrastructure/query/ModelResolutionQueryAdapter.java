package br.com.qsoft.governance.aigateway.infrastructure.query;

import br.com.qsoft.governance.aigateway.api.exception.ModelNotAllowedException;
import br.com.qsoft.governance.aigateway.api.exception.ModelUnavailableException;
import br.com.qsoft.governance.aigateway.api.exception.NoModelConfiguredException;
import br.com.qsoft.governance.aigateway.api.exception.ProviderInactiveException;
import br.com.qsoft.governance.aigateway.api.model.ResolvedModelInfo;
import br.com.qsoft.governance.aigateway.api.query.ModelResolutionQuery;
import br.com.qsoft.governance.aigateway.application.ResolveModelForUser;
import br.com.qsoft.governance.aigateway.application.ResolvedModel;
import br.com.qsoft.governance.aigateway.domain.exception.ModelNotFoundException;
import br.com.qsoft.governance.aigateway.domain.exception.ProviderNotFoundException;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adapter do contrato público sobre o roteamento determinístico existente. */
@Component
public class ModelResolutionQueryAdapter implements ModelResolutionQuery {

    private final ResolveModelForUser resolveModelForUser;

    public ModelResolutionQueryAdapter(ResolveModelForUser resolveModelForUser) {
        this.resolveModelForUser = resolveModelForUser;
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
}
