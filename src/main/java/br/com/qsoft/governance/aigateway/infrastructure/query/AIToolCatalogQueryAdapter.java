package br.com.qsoft.governance.aigateway.infrastructure.query;

import br.com.qsoft.governance.aigateway.api.model.CatalogModelInfo;
import br.com.qsoft.governance.aigateway.api.query.AIToolCatalogQuery;
import br.com.qsoft.governance.aigateway.application.port.ModelRepository;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adapter que satisfaz o contrato público {@link AIToolCatalogQuery}. */
@Component
public class AIToolCatalogQueryAdapter implements AIToolCatalogQuery {

    private final ModelRepository modelRepository;

    public AIToolCatalogQueryAdapter(ModelRepository modelRepository) {
        this.modelRepository = modelRepository;
    }

    @Override
    public Optional<CatalogModelInfo> findModel(TenantId tenantId, UUID modelId) {
        return modelRepository.findByTenantIdAndId(tenantId, modelId).map(this::toInfo);
    }

    @Override
    public boolean allModelsExist(TenantId tenantId, Collection<UUID> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return true;
        }
        return modelRepository.findByTenantIdAndIdIn(tenantId, modelIds).size() == modelIds.size();
    }

    private CatalogModelInfo toInfo(Model model) {
        return new CatalogModelInfo(
            model.id(),
            model.providerId(),
            model.name(),
            model.status().name()
        );
    }
}
