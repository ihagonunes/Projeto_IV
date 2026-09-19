package br.com.qsoft.governance.iam.infrastructure.persistence;

import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter de persistência que satisfaz a porta UserRepository do módulo IAM.
 * <p>
 * Quando um {@link TenantContext} está presente, ativa o filtro Hibernate
 * transversal (tenant_id) como defesa secundária de isolamento (ADR-03).
 * A defesa primária é o filtro explícito por tenant_id nas queries
 * (findByTenantIdAndEmail / existsByTenantIdAndEmail).
 */
@Component
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final UserJpaMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    public UserRepositoryAdapter(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = new UserJpaMapper();
    }

    @Override
    @Transactional
    public User save(User user) {
        UserJpaEntity entity = jpaRepository.save(mapper.toEntity(user));
        return mapper.toDomain(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UserId id) {
        TenantContext context = TenantContextHolder.get();
        if (context != null) {
            // @Filter não se aplica ao find() por PK; restrição explícita por tenant.
            return jpaRepository.findByIdAndTenantId(id.value(), context.tenantId().value())
                .map(mapper::toDomain);
        }
        // Sem TenantContext (ex.: revalidação do token), consulta sem restrição.
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByTenantIdAndEmail(TenantId tenantId, String email) {
        applyTenantFilter();
        return jpaRepository.findByTenantIdAndEmail(tenantId.value(), email).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByTenantIdAndEmail(TenantId tenantId, String email) {
        applyTenantFilter();
        return jpaRepository.existsByTenantIdAndEmail(tenantId.value(), email);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<User> findAllByTenantId(TenantId tenantId) {
        applyTenantFilter();
        return jpaRepository.findAllByTenantId(tenantId.value())
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    /**
     * Ativa o filtro de isolamento por tenant_id apenas quando existe um
     * TenantContext (request autenticado). Durante bootstrap/login o contexto
     * é nulo e o filtro fica inativo.
     */
    private void applyTenantFilter() {
        TenantContext context = TenantContextHolder.get();
        if (context == null) {
            return;
        }
        Session session = entityManager.unwrap(Session.class);
        Filter filter = session.getEnabledFilter(UserJpaEntity.TENANT_FILTER_NAME);
        if (filter == null) {
            filter = session.enableFilter(UserJpaEntity.TENANT_FILTER_NAME);
        }
        filter.setParameter("tenantId", context.tenantId().value());
    }
}
