package br.com.qsoft.governance.iam.infrastructure.persistence;

import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.application.port.UserSearchFilter;
import br.com.qsoft.governance.iam.application.port.UserSearchRepository;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
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
public class UserRepositoryAdapter implements UserRepository, UserSearchRepository {

    private static final char LIKE_ESCAPE = '\\';

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
        // SEMPRE tenant-scoped: o tenant vem do contexto autenticado (ADR-03).
        // O caminho de autenticação (sem contexto) usa findByIdUnscoped.
        TenantContext context = TenantContextHolder.get();
        if (context == null) {
            throw new IllegalStateException(
                "TenantContext ausente: use findByIdUnscoped apenas no caminho de autenticação.");
        }
        // @Filter não se aplica ao find() por PK; restrição explícita por tenant.
        return jpaRepository.findByIdAndTenantId(id.value(), context.tenantId().value())
            .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByIdUnscoped(UserId id) {
        // Uso EXCLUSIVO do TenantContextFilter (o contexto ainda não existe).
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByTenantIdAndId(TenantId tenantId, UserId id) {
        // Filtro explícito por tenant, sem depender do TenantContext (aceite de convite).
        return jpaRepository.findByIdAndTenantId(id.value(), tenantId.value()).map(mapper::toDomain);
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
    public List<User> findActiveByTenantId(TenantId tenantId) {
        applyTenantFilter();
        return jpaRepository
            .findByTenantIdAndStatusOrderByNameAscIdAsc(tenantId.value(), UserStatus.ACTIVE)
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<User> search(TenantId tenantId, UserSearchFilter filter, int page, int size) {
        applyTenantFilter();
        Page<UserJpaEntity> result = jpaRepository.findAll(
            tenantScopedSpecification(tenantId, filter), PageRequest.of(page, size));
        List<User> users = result.getContent().stream().map(mapper::toDomain).toList();
        return new PageResult<>(users, result.getTotalElements(), page, size);
    }

    /**
     * Defesa primária de isolamento: o predicado por tenant_id é SEMPRE aplicado
     * à consulta, além do filtro Hibernate (defesa secundária, ADR-03).
     */
    private Specification<UserJpaEntity> tenantScopedSpecification(TenantId tenantId, UserSearchFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("tenantId"), tenantId.value()));
            if (filter != null) {
                // Recorte de visibilidade (derivado de visibilityFor — nunca do cliente).
                if (filter.visibleUserId() != null && filter.visibleDepartmentIds().isEmpty()) {
                    predicates.add(builder.equal(root.get("id"), filter.visibleUserId()));
                } else if (filter.visibleUserId() != null) {
                    predicates.add(builder.or(
                        root.get("departmentId").in(filter.visibleDepartmentIds()),
                        builder.equal(root.get("id"), filter.visibleUserId())));
                } else if (!filter.visibleDepartmentIds().isEmpty()) {
                    predicates.add(root.get("departmentId").in(filter.visibleDepartmentIds()));
                }
                if (filter.hierarchyLevel() != null) {
                    predicates.add(builder.equal(root.get("hierarchyLevel"), filter.hierarchyLevel()));
                }
                if (filter.departmentId() != null) {
                    predicates.add(builder.equal(root.get("departmentId"), filter.departmentId()));
                }
                if (hasText(filter.name())) {
                    predicates.add(builder.like(
                        builder.lower(root.get("name").as(String.class)),
                        likePattern(filter.name()), LIKE_ESCAPE));
                }
                if (hasText(filter.email())) {
                    predicates.add(builder.like(
                        builder.lower(root.get("email").as(String.class)),
                        likePattern(filter.email()), LIKE_ESCAPE));
                }
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Monta o padrão LIKE escapando os curingas digitados pelo usuário
     * ({@code %}, {@code _} e {@code \}) para que a busca seja literal. O valor
     * continua vinculado como parâmetro — não há injeção de SQL, apenas
     * semântica de busca previsível.
     */
    private static String likePattern(String value) {
        String escaped = value.trim().toLowerCase()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
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
