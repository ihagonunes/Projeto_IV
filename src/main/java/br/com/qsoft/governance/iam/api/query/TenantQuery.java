package br.com.qsoft.governance.iam.api.query;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Optional;

/** Consulta pública mínima para resolver um tenant por slug (seeders/demos). */
public interface TenantQuery {

    Optional<TenantId> findBySlug(String slug);
}
