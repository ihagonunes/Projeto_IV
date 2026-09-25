package br.com.qsoft.governance.aigateway.application.port;

import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saída do módulo {@code aigateway} para persistência de Model.
 * Todas as consultas são tenant-scoped.
 */
public interface ModelRepository {

    Model save(Model model);

    Optional<Model> findByTenantIdAndId(TenantId tenantId, UUID modelId);

    List<Model> findByTenantId(TenantId tenantId);

    /** Busca em lote — usada pelo routing para validar o conjunto permitido. */
    List<Model> findByTenantIdAndIdIn(TenantId tenantId, Collection<UUID> modelIds);

    boolean existsByTenantIdAndName(TenantId tenantId, String name);
}
