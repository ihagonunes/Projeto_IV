package br.com.qsoft.governance.observability.infrastructure.persistence;

import br.com.qsoft.governance.observability.application.port.UsageRecordRepository;
import br.com.qsoft.governance.observability.domain.UsageRecord;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter de persistência do UsageRecord. Defesa primária: predicado explícito
 * por tenant_id; defesa secundária: filtro Hibernate quando há TenantContext.
 */
@Component
public class UsageRecordRepositoryAdapter implements UsageRecordRepository {

    private final UsageRecordJpaRepository jpaRepository;
    private final UsageRecordJpaMapper mapper = new UsageRecordJpaMapper();

    @PersistenceContext
    private EntityManager entityManager;

    public UsageRecordRepositoryAdapter(UsageRecordJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public UsageRecord save(UsageRecord record) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(record)));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByRequestId(UUID requestId) {
        return jpaRepository.existsByRequestId(requestId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<UsageRecord> search(UsageRecordFilter filter, int page, int size) {
        applyTenantFilter();
        Page<UsageRecordJpaEntity> result = jpaRepository.findAll(
            specification(filter),
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResult<>(
            result.getContent().stream().map(mapper::toDomain).toList(),
            result.getTotalElements(), page, size);
    }

    private Specification<UsageRecordJpaEntity> specification(UsageRecordFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("tenantId"), filter.tenantId().value()));
            appendVisibility(predicates, root, builder, filter.visibility());
            if (filter.departmentId() != null) {
                predicates.add(builder.equal(root.get("departmentId"), filter.departmentId()));
            }
            if (filter.userId() != null) {
                predicates.add(builder.equal(root.get("userId"), filter.userId()));
            }
            if (filter.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), filter.to()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void appendVisibility(List<Predicate> predicates,
                                  jakarta.persistence.criteria.Root<UsageRecordJpaEntity> root,
                                  jakarta.persistence.criteria.CriteriaBuilder builder,
                                  br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope visibility) {
        if (visibility instanceof br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope.TenantWide) {
            return;
        }
        if (visibility instanceof br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope.Departments departments) {
            predicates.add(builder.or(
                root.get("departmentId").in(departments.ids()),
                builder.equal(root.get("userId"), departments.self().value())));
            return;
        }
        if (visibility instanceof br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope.SelfOnly selfOnly) {
            predicates.add(builder.equal(root.get("userId"), selfOnly.self().value()));
            return;
        }
        throw new br.com.qsoft.governance.observability.application.UsageVisibilityDeniedException();
    }

    void applyTenantFilter() {
        TenantContext context = TenantContextHolder.get();
        if (context == null) {
            return;
        }
        Session session = entityManager.unwrap(Session.class);
        Filter filter = session.getEnabledFilter(UsageRecordJpaEntity.TENANT_FILTER_NAME);
        if (filter == null) {
            filter = session.enableFilter(UsageRecordJpaEntity.TENANT_FILTER_NAME);
        }
        filter.setParameter("tenantId", context.tenantId().value());
    }
}
