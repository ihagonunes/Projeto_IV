package br.com.qsoft.governance.department.infrastructure.persistence;

import br.com.qsoft.governance.department.application.port.DepartmentRepository;
import br.com.qsoft.governance.department.domain.Department;
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
 * Adapter de persistência que satisfaz a porta DepartmentRepository.
 * <p>
 * A defesa primária de isolamento é o predicado explícito por {@code tenant_id}
 * em todas as consultas; quando há {@link TenantContext}, o filtro Hibernate é
 * ativado como defesa secundária (ADR-03).
 */
@Component
public class DepartmentRepositoryAdapter implements DepartmentRepository {

    private final DepartmentJpaRepository jpaRepository;
    private final DepartmentJpaMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    public DepartmentRepositoryAdapter(DepartmentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = new DepartmentJpaMapper();
    }

    @Override
    @Transactional
    public Department save(Department department) {
        DepartmentJpaEntity entity = jpaRepository.save(mapper.toEntity(department));
        return mapper.toDomain(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Department> findByTenantIdAndId(TenantId tenantId, UUID departmentId) {
        applyTenantFilter();
        return jpaRepository.findByTenantIdAndId(tenantId.value(), departmentId)
            .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Department> findByTenantId(TenantId tenantId) {
        applyTenantFilter();
        return jpaRepository.findByTenantId(tenantId.value()).stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByTenantIdAndName(TenantId tenantId, String name) {
        applyTenantFilter();
        return jpaRepository.existsByTenantIdAndName(tenantId.value(), name);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByTenantIdAndNameAndIdNot(TenantId tenantId, String name, UUID departmentId) {
        applyTenantFilter();
        return jpaRepository.existsByTenantIdAndNameAndIdNot(tenantId.value(), name, departmentId);
    }

    /**
     * Ativa o filtro de isolamento por tenant_id apenas quando existe um
     * TenantContext (request autenticado). Sem contexto, a consulta permanece
     * restrita pelo predicado explícito por tenant_id.
     */
    private void applyTenantFilter() {
        TenantContext context = TenantContextHolder.get();
        if (context == null) {
            return;
        }
        Session session = entityManager.unwrap(Session.class);
        Filter filter = session.getEnabledFilter(DepartmentJpaEntity.TENANT_FILTER_NAME);
        if (filter == null) {
            filter = session.enableFilter(DepartmentJpaEntity.TENANT_FILTER_NAME);
        }
        filter.setParameter("tenantId", context.tenantId().value());
    }
}
