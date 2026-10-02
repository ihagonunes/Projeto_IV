package br.com.qsoft.governance.aigateway.infrastructure.persistence;

import br.com.qsoft.governance.aigateway.application.port.ModelRepository;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter de persistência do Model. Defesa primária: predicado explícito por
 * tenant_id; defesa secundária: filtro Hibernate quando há TenantContext.
 */
@Component
public class ModelRepositoryAdapter implements ModelRepository {

    private final ModelJpaRepository jpaRepository;
    private final ModelJpaMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    public ModelRepositoryAdapter(ModelJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = new ModelJpaMapper();
    }

    @Override
    @Transactional
    public Model save(Model model) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(model)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Model> findByTenantIdAndId(TenantId tenantId, UUID modelId) {
        applyTenantFilter();
        return jpaRepository.findByTenantIdAndId(tenantId.value(), modelId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Model> findByTenantId(TenantId tenantId) {
        applyTenantFilter();
        return jpaRepository.findByTenantId(tenantId.value()).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Model> findByTenantIdAndIdIn(TenantId tenantId, Collection<UUID> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return List.of();
        }
        applyTenantFilter();
        return jpaRepository.findByTenantIdAndIdIn(tenantId.value(), modelIds).stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByTenantIdAndName(TenantId tenantId, String name) {
        applyTenantFilter();
        return jpaRepository.existsByTenantIdAndName(tenantId.value(), name);
    }

    void applyTenantFilter() {
        TenantContext context = TenantContextHolder.get();
        if (context == null) {
            return;
        }
        Session session = entityManager.unwrap(Session.class);
        Filter filter = session.getEnabledFilter(ModelJpaEntity.TENANT_FILTER_NAME);
        if (filter == null) {
            filter = session.enableFilter(ModelJpaEntity.TENANT_FILTER_NAME);
        }
        filter.setParameter("tenantId", context.tenantId().value());
    }
}
