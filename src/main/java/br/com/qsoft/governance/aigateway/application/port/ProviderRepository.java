package br.com.qsoft.governance.aigateway.application.port;

import br.com.qsoft.governance.aigateway.domain.provider.Provider;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saída do módulo {@code aigateway} para persistência de Provider.
 * Todas as consultas são tenant-scoped.
 */
public interface ProviderRepository {

    Provider save(Provider provider);

    Optional<Provider> findByTenantIdAndId(TenantId tenantId, UUID providerId);

    List<Provider> findByTenantId(TenantId tenantId);

    boolean existsByTenantIdAndName(TenantId tenantId, String name);
}
