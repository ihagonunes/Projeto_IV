package br.com.qsoft.governance.aigateway.api.query;

import br.com.qsoft.governance.aigateway.api.model.CatalogModelInfo;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato público do módulo {@code aigateway} para consulta do catálogo de
 * modelos (ADR-09). Outros módulos validam/consultam ModelIds aqui, em vez de
 * acessar a persistência do gateway. O {@code tenantId} é explícito (ADR-03).
 */
public interface AIToolCatalogQuery {

    Optional<CatalogModelInfo> findModel(TenantId tenantId, UUID modelId);

    /** {@code true} somente se <b>todos</b> os IDs existirem no tenant. */
    boolean allModelsExist(TenantId tenantId, Collection<UUID> modelIds);
}
