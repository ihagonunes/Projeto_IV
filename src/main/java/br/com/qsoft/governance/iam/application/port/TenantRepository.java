package br.com.qsoft.governance.iam.application.port;

import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Optional;

/**
 * Porta de saída do módulo IAM para persistência de Tenant.
 * Implementada por um adapter na camada de infraestrutura.
 */
public interface TenantRepository {

    Tenant save(Tenant tenant);

    Optional<Tenant> findById(TenantId id);

    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
