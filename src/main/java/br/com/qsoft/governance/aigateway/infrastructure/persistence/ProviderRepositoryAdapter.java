package br.com.qsoft.governance.aigateway.infrastructure.persistence;

import br.com.qsoft.governance.aigateway.application.port.ProviderRepository;
import br.com.qsoft.governance.aigateway.domain.provider.Provider;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter de persistência do Provider. Defesa primária: predicado explícito por
 * tenant_id; defesa secundária: filtro Hibernate quando há TenantContext.
 */
@Component
public class ProviderRepositoryAdapter implements ProviderRepository {

    private final ProviderJpaRepository jpaRepository;
    private final ProviderJpaMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    public ProviderRepositoryAdapter(ProviderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = new ProviderJpaMapper();
    }

    @Override
    @Transactional
    public Provider save(Provider provider) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(provider)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Provider> findByTenantIdAndId(TenantId tenantId, UUID providerId) {
        applyTenantFilter();
        return jpaRepository.findByTenantIdAndId(tenantId.value(), providerId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Provider> findByTenantId(TenantId tenantId) {
        applyTenantFilter();
        return jpaRepository.findByTenantId(tenantId.value()).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByTenantIdAndName(TenantId tenantId, String name) {
        applyTenantFilter();
        return jpaRepository.existsByTenantIdAndName(tenantId.value(), name);
    }

    private void applyTenantFilter() {
        TenantContext context = TenantContextHolder.get();
        if (context == null) {
            return;
        }
        Session session = entityManager.unwrap(Session.class);
        Filter filter = session.getEnabledFilter(ProviderJpaEntity.TENANT_FILTER_NAME);
        if (filter == null) {
            filter = session.enableFilter(ProviderJpaEntity.TENANT_FILTER_NAME);
        }
        filter.setParameter("tenantId", context.tenantId().value());
    }
}
