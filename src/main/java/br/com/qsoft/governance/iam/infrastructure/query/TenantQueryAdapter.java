package br.com.qsoft.governance.iam.infrastructure.query;

import br.com.qsoft.governance.iam.api.query.TenantQuery;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Adapter do contrato público de resolução de tenant por slug. */
@Component
public class TenantQueryAdapter implements TenantQuery {

    private final TenantRepository tenantRepository;

    public TenantQueryAdapter(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public Optional<TenantId> findBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return tenantRepository.findBySlug(slug).map(tenant -> tenant.id());
    }
}
