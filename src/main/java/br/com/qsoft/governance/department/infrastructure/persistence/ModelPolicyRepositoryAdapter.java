package br.com.qsoft.governance.department.infrastructure.persistence;

import br.com.qsoft.governance.department.application.port.ModelPolicyRepository;
import br.com.qsoft.governance.department.domain.modelpolicy.ModelPolicy;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter de persistência da ModelPolicy. Defesa primária: predicado explícito
 * por tenant_id; defesa secundária: filtro Hibernate quando há TenantContext.
 */
@Component
public class ModelPolicyRepositoryAdapter implements ModelPolicyRepository {

    private final ModelPolicyJpaRepository jpaRepository;
    private final ModelPolicyJpaMapper mapper = new ModelPolicyJpaMapper();

    @PersistenceContext
    private EntityManager entityManager;

    public ModelPolicyRepositoryAdapter(ModelPolicyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public ModelPolicy save(ModelPolicy policy) {
        // O tenant do contexto é quem define a coluna tenant_id (ADR-03).
        UUID tenantId = TenantContextHolder.require().tenantId().value();
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(policy, tenantId)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ModelPolicy> findByTenantIdAndDepartmentId(TenantId tenantId, UUID departmentId) {
        applyTenantFilter();
        return jpaRepository.findByTenantIdAndDepartmentId(tenantId.value(), departmentId)
            .map(mapper::toDomain);
    }

    private void applyTenantFilter() {
        TenantContext context = TenantContextHolder.get();
        if (context == null) {
            return;
        }
        Session session = entityManager.unwrap(Session.class);
        Filter filter = session.getEnabledFilter(ModelPolicyJpaEntity.TENANT_FILTER_NAME);
        if (filter == null) {
            filter = session.enableFilter(ModelPolicyJpaEntity.TENANT_FILTER_NAME);
        }
        filter.setParameter("tenantId", context.tenantId().value());
    }
}
