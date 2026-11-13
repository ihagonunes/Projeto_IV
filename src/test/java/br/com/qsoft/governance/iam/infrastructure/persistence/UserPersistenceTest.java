package br.com.qsoft.governance.iam.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.application.port.UserSearchFilter;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserPersistenceTest {

    @Mock
    private UserJpaRepository jpaRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Session session;

    @Mock
    private Filter filter;

    @InjectMocks
    private UserRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adapter, "entityManager", entityManager);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void explicitlyScopesIdAndActiveUserLookupsByTenantWithoutThreadContext() {
        TenantId tenant = TenantId.random();
        User user = createSampleUserWith(UserId.random(), tenant);
        UserJpaEntity entity = new UserJpaMapper().toEntity(user);
        when(jpaRepository.findByIdAndTenantId(user.id().value(), tenant.value())).thenReturn(Optional.of(entity));
        when(jpaRepository.findByTenantIdAndStatusOrderByNameAscIdAsc(tenant.value(), UserStatus.ACTIVE))
            .thenReturn(List.of(entity));

        assertThat(adapter.findByTenantIdAndId(tenant, user.id())).hasValueSatisfying(found ->
            assertThat(found.tenantId()).isEqualTo(tenant));
        assertThat(adapter.findByTenantIdAndId(TenantId.random(), user.id())).isEmpty();
        assertThat(adapter.findActiveByTenantId(tenant)).extracting(User::id).containsExactly(user.id());
    }

    @ParameterizedTest
    @CsvSource({"true,false", "true,true", "false,true"})
    void searchIntersectsTenantWithUserAndDepartmentVisibility(boolean selfVisible, boolean departmentVisible) {
        TenantId tenant = TenantId.random();
        UUID user = UUID.randomUUID();
        Set<UUID> departments = Set.of(UUID.randomUUID());
        var filter = new UserSearchFilter(null, null, null, null, departmentVisible ? departments : Set.of(),
            selfVisible ? user : null);
        PageRequest page = PageRequest.of(0, 10);
        when(jpaRepository.findAll(any(Specification.class), eq(page))).thenReturn(new PageImpl<>(List.of()));
        adapter.search(tenant, filter, 0, 10);
        ArgumentCaptor<Specification<UserJpaEntity>> spec = ArgumentCaptor.captor();
        verify(jpaRepository).findAll(spec.capture(), eq(page));

        Root<UserJpaEntity> root = mock();
        CriteriaBuilder builder = mock();
        Path<UUID> tenantPath = mock();
        Predicate tenantPredicate = mock();
        when(root.<UUID>get("tenantId")).thenReturn(tenantPath);
        when(builder.equal(tenantPath, tenant.value())).thenReturn(tenantPredicate);
        Predicate selfPredicate = mock();
        if (selfVisible) {
            Path<UUID> userPath = mock();
            when(root.<UUID>get("id")).thenReturn(userPath);
            when(builder.equal(userPath, user)).thenReturn(selfPredicate);
        }
        Predicate departmentPredicate = mock();
        if (departmentVisible) {
            Path<UUID> departmentPath = mock();
            when(root.<UUID>get("departmentId")).thenReturn(departmentPath);
            when(departmentPath.in(departments)).thenReturn(departmentPredicate);
        }
        Predicate visibility = selfVisible ? selfPredicate : departmentPredicate;
        if (selfVisible && departmentVisible) {
            visibility = mock();
            when(builder.or(departmentPredicate, selfPredicate)).thenReturn(visibility);
        }

        spec.getValue().toPredicate(root, null, builder);

        ArgumentCaptor<Predicate[]> predicates = ArgumentCaptor.forClass(Predicate[].class);
        verify(builder).and(predicates.capture());
        assertThat(predicates.getValue()).containsExactly(tenantPredicate, visibility);
    }

    @Test
    void deve_testarUserJpaEntity_gettersESetters() {
        UserJpaEntity entity = new UserJpaEntity();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Instant now = Instant.now();

        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setEmail("user@qsoft.com");
        entity.setName("User Name");
        entity.setHierarchyLevel(HierarchyLevel.MANAGER);
        entity.setDepartmentId(deptId);
        entity.setPasswordHash("hash123");
        entity.setStatus(UserStatus.ACTIVE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getTenantId()).isEqualTo(tenantId);
        assertThat(entity.getEmail()).isEqualTo("user@qsoft.com");
        assertThat(entity.getName()).isEqualTo("User Name");
        assertThat(entity.getHierarchyLevel()).isEqualTo(HierarchyLevel.MANAGER);
        assertThat(entity.getDepartmentId()).isEqualTo(deptId);
        assertThat(entity.getPasswordHash()).isEqualTo("hash123");
        assertThat(entity.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void deve_converterEntreUserEEntity_comUserJpaMapper() {
        UserJpaMapper mapper = new UserJpaMapper();

        UserId userId = UserId.random();
        TenantId tenantId = TenantId.random();
        UUID deptId = UUID.randomUUID();
        Instant now = Instant.now();

        User user = User.restore(
            userId,
            tenantId,
            "dev@qsoft.com",
            "Developer",
            HierarchyLevel.COLLABORATOR,
            deptId,
            "passhash",
            UserStatus.ACTIVE,
            now,
            now
        );

        UserJpaEntity entity = mapper.toEntity(user);
        assertThat(entity.getId()).isEqualTo(userId.value());
        assertThat(entity.getTenantId()).isEqualTo(tenantId.value());
        assertThat(entity.getEmail()).isEqualTo("dev@qsoft.com");

        User restoredUser = mapper.toDomain(entity);
        assertThat(restoredUser.id()).isEqualTo(userId);
        assertThat(restoredUser.tenantId()).isEqualTo(tenantId);
        assertThat(restoredUser.email()).isEqualTo("dev@qsoft.com");
        assertThat(restoredUser.name()).isEqualTo("Developer");
        assertThat(restoredUser.hierarchyLevel()).isEqualTo(HierarchyLevel.COLLABORATOR);
        assertThat(restoredUser.departmentId()).isEqualTo(deptId);
        assertThat(restoredUser.passwordHash()).isEqualTo("passhash");
        assertThat(restoredUser.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void deve_salvarUsuario() {
        User user = createSampleUser();

        when(jpaRepository.save(any(UserJpaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User saved = adapter.save(user);

        assertThat(saved.id()).isEqualTo(user.id());
        verify(jpaRepository).save(any(UserJpaEntity.class));
    }

    @Test
    void deve_lancarExcecao_quando_findByIdSemTenantContext() {
        UserId id = UserId.random();
        assertThatThrownBy(() -> adapter.findById(id))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("TenantContext ausente");
    }

    @Test
    void deve_buscarPorId_quando_tenantContextPresente() {
        TenantId tenantId = TenantId.random();
        UserId currentUserId = UserId.random();
        TenantContextHolder.set(new TenantContext(tenantId, currentUserId));

        UserId userId = UserId.random();
        User user = createSampleUserWith(userId, tenantId);
        UserJpaMapper mapper = new UserJpaMapper();
        UserJpaEntity entity = mapper.toEntity(user);

        when(jpaRepository.findByIdAndTenantId(userId.value(), tenantId.value()))
            .thenReturn(Optional.of(entity));

        Optional<User> result = adapter.findById(userId);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(userId);
    }

    @Test
    void deve_buscarPorIdUnscoped() {
        UserId userId = UserId.random();
        TenantId tenantId = TenantId.random();
        User user = createSampleUserWith(userId, tenantId);
        UserJpaMapper mapper = new UserJpaMapper();
        UserJpaEntity entity = mapper.toEntity(user);

        when(jpaRepository.findById(userId.value())).thenReturn(Optional.of(entity));

        Optional<User> result = adapter.findByIdUnscoped(userId);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(userId);
    }

    @Test
    void deve_buscarPorTenantIdEEmail_ativandoFiltroExistente() {
        TenantId tenantId = TenantId.random();
        UserId currentUserId = UserId.random();
        TenantContextHolder.set(new TenantContext(tenantId, currentUserId));

        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.getEnabledFilter(UserJpaEntity.TENANT_FILTER_NAME)).thenReturn(filter);

        User user = createSampleUserWith(UserId.random(), tenantId);
        UserJpaEntity entity = new UserJpaMapper().toEntity(user);

        when(jpaRepository.findByTenantIdAndEmail(tenantId.value(), "dev@qsoft.com"))
            .thenReturn(Optional.of(entity));

        Optional<User> result = adapter.findByTenantIdAndEmail(tenantId, "dev@qsoft.com");

        assertThat(result).isPresent();
        verify(filter).setParameter("tenantId", tenantId.value());
    }

    @Test
    void deve_buscarPorTenantIdEEmail_ativandoFiltroNovo() {
        TenantId tenantId = TenantId.random();
        UserId currentUserId = UserId.random();
        TenantContextHolder.set(new TenantContext(tenantId, currentUserId));

        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.getEnabledFilter(UserJpaEntity.TENANT_FILTER_NAME)).thenReturn(null);
        when(session.enableFilter(UserJpaEntity.TENANT_FILTER_NAME)).thenReturn(filter);

        when(jpaRepository.findByTenantIdAndEmail(tenantId.value(), "dev@qsoft.com"))
            .thenReturn(Optional.empty());

        Optional<User> result = adapter.findByTenantIdAndEmail(tenantId, "dev@qsoft.com");

        assertThat(result).isEmpty();
        verify(filter).setParameter("tenantId", tenantId.value());
    }

    @Test
    void deve_verificarExistsByTenantIdAndEmail() {
        TenantId tenantId = TenantId.random();
        when(jpaRepository.existsByTenantIdAndEmail(tenantId.value(), "dev@qsoft.com")).thenReturn(true);

        boolean exists = adapter.existsByTenantIdAndEmail(tenantId, "dev@qsoft.com");

        assertThat(exists).isTrue();
    }

    @Test
    void deve_buscarComPaginacaoEFilterCompleto() {
        TenantId tenantId = TenantId.random();
        UUID deptId = UUID.randomUUID();

        UserSearchFilter filter = new UserSearchFilter(
            HierarchyLevel.MANAGER,
            deptId,
            "  John\\_%  ",
            "  john@qsoft.com  ",
            null,
            null
        );

        User user = createSampleUserWith(UserId.random(), tenantId);
        UserJpaEntity entity = new UserJpaMapper().toEntity(user);

        when(jpaRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 10))))
            .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 10), 1));

        PageResult<User> result = adapter.search(tenantId, filter, 0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void deve_buscarComPaginacaoEFilterNulo() {
        TenantId tenantId = TenantId.random();

        when(jpaRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 5))))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 5), 0));

        PageResult<User> result = adapter.search(tenantId, null, 0, 5);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    void deve_testarSpecificationLambda_comFiltroCompleto() throws Exception {
        TenantId tenantId = TenantId.random();
        UUID deptId = UUID.randomUUID();
        UserSearchFilter filter = new UserSearchFilter(
            HierarchyLevel.LEADER,
            deptId,
            "  Alice\\_%  ",
            "  alice@qsoft.com  ",
            null,
            null
        );

        org.mockito.ArgumentCaptor<Specification<UserJpaEntity>> captor =
            org.mockito.ArgumentCaptor.forClass(Specification.class);

        when(jpaRepository.findAll(captor.capture(), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of()));

        adapter.search(tenantId, filter, 0, 10);

        Specification<UserJpaEntity> spec = captor.getValue();
        assertThat(spec).isNotNull();

        jakarta.persistence.criteria.Root<UserJpaEntity> root = mock(jakarta.persistence.criteria.Root.class);
        jakarta.persistence.criteria.CriteriaQuery<?> query = mock(jakarta.persistence.criteria.CriteriaQuery.class);
        jakarta.persistence.criteria.CriteriaBuilder builder = mock(jakarta.persistence.criteria.CriteriaBuilder.class);

        jakarta.persistence.criteria.Path<Object> pathTenantId = mock(jakarta.persistence.criteria.Path.class);
        jakarta.persistence.criteria.Path<Object> pathHierarchy = mock(jakarta.persistence.criteria.Path.class);
        jakarta.persistence.criteria.Path<Object> pathDept = mock(jakarta.persistence.criteria.Path.class);
        jakarta.persistence.criteria.Path<Object> pathName = mock(jakarta.persistence.criteria.Path.class);
        jakarta.persistence.criteria.Path<Object> pathEmail = mock(jakarta.persistence.criteria.Path.class);

        jakarta.persistence.criteria.Expression<String> exprNameStr = mock(jakarta.persistence.criteria.Expression.class);
        jakarta.persistence.criteria.Expression<String> exprNameLower = mock(jakarta.persistence.criteria.Expression.class);
        jakarta.persistence.criteria.Expression<String> exprEmailStr = mock(jakarta.persistence.criteria.Expression.class);
        jakarta.persistence.criteria.Expression<String> exprEmailLower = mock(jakarta.persistence.criteria.Expression.class);

        jakarta.persistence.criteria.Predicate predEqTenant = mock(jakarta.persistence.criteria.Predicate.class);
        jakarta.persistence.criteria.Predicate predEqHierarchy = mock(jakarta.persistence.criteria.Predicate.class);
        jakarta.persistence.criteria.Predicate predEqDept = mock(jakarta.persistence.criteria.Predicate.class);
        jakarta.persistence.criteria.Predicate predLikeName = mock(jakarta.persistence.criteria.Predicate.class);
        jakarta.persistence.criteria.Predicate predLikeEmail = mock(jakarta.persistence.criteria.Predicate.class);
        jakarta.persistence.criteria.Predicate predAnd = mock(jakarta.persistence.criteria.Predicate.class);

        when(root.get("tenantId")).thenReturn(pathTenantId);
        when(builder.equal(pathTenantId, tenantId.value())).thenReturn(predEqTenant);

        when(root.get("hierarchyLevel")).thenReturn(pathHierarchy);
        when(builder.equal(pathHierarchy, HierarchyLevel.LEADER)).thenReturn(predEqHierarchy);

        when(root.get("departmentId")).thenReturn(pathDept);
        when(builder.equal(pathDept, deptId)).thenReturn(predEqDept);

        when(root.get("name")).thenReturn(pathName);
        when(pathName.as(String.class)).thenReturn(exprNameStr);
        when(builder.lower(exprNameStr)).thenReturn(exprNameLower);
        when(builder.like(eq(exprNameLower), eq("%alice\\\\\\_\\%%"), eq('\\'))).thenReturn(predLikeName);

        when(root.get("email")).thenReturn(pathEmail);
        when(pathEmail.as(String.class)).thenReturn(exprEmailStr);
        when(builder.lower(exprEmailStr)).thenReturn(exprEmailLower);
        when(builder.like(eq(exprEmailLower), eq("%alice@qsoft.com%"), eq('\\'))).thenReturn(predLikeEmail);

        when(builder.and(any(jakarta.persistence.criteria.Predicate[].class))).thenReturn(predAnd);

        jakarta.persistence.criteria.Predicate resultPredicate = spec.toPredicate(root, query, builder);
        assertThat(resultPredicate).isEqualTo(predAnd);
    }

    @Test
    void deve_testarSpecificationLambda_comFiltroComCamposNulosEEmBranco() throws Exception {
        TenantId tenantId = TenantId.random();
        UserSearchFilter filter = new UserSearchFilter(
            null,
            null,
            "   ",
            null,
            null,
            null
        );

        org.mockito.ArgumentCaptor<Specification<UserJpaEntity>> captor =
            org.mockito.ArgumentCaptor.forClass(Specification.class);

        when(jpaRepository.findAll(captor.capture(), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of()));

        adapter.search(tenantId, filter, 0, 10);

        Specification<UserJpaEntity> spec = captor.getValue();
        assertThat(spec).isNotNull();

        jakarta.persistence.criteria.Root<UserJpaEntity> root = mock(jakarta.persistence.criteria.Root.class);
        jakarta.persistence.criteria.CriteriaQuery<?> query = mock(jakarta.persistence.criteria.CriteriaQuery.class);
        jakarta.persistence.criteria.CriteriaBuilder builder = mock(jakarta.persistence.criteria.CriteriaBuilder.class);

        jakarta.persistence.criteria.Path<Object> pathTenantId = mock(jakarta.persistence.criteria.Path.class);
        jakarta.persistence.criteria.Predicate predEqTenant = mock(jakarta.persistence.criteria.Predicate.class);
        jakarta.persistence.criteria.Predicate predAnd = mock(jakarta.persistence.criteria.Predicate.class);

        when(root.get("tenantId")).thenReturn(pathTenantId);
        when(builder.equal(pathTenantId, tenantId.value())).thenReturn(predEqTenant);
        when(builder.and(any(jakarta.persistence.criteria.Predicate[].class))).thenReturn(predAnd);

        jakarta.persistence.criteria.Predicate resultPredicate = spec.toPredicate(root, query, builder);
        assertThat(resultPredicate).isEqualTo(predAnd);
    }

    @Test
    void deve_testarSpecificationLambda_comFilterNulo() {
        TenantId tenantId = TenantId.random();

        org.mockito.ArgumentCaptor<Specification<UserJpaEntity>> captor =
            org.mockito.ArgumentCaptor.forClass(Specification.class);

        when(jpaRepository.findAll(captor.capture(), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of()));

        adapter.search(tenantId, null, 0, 10);

        Specification<UserJpaEntity> spec = captor.getValue();
        assertThat(spec).isNotNull();

        jakarta.persistence.criteria.Root<UserJpaEntity> root = mock(jakarta.persistence.criteria.Root.class);
        jakarta.persistence.criteria.CriteriaQuery<?> query = mock(jakarta.persistence.criteria.CriteriaQuery.class);
        jakarta.persistence.criteria.CriteriaBuilder builder = mock(jakarta.persistence.criteria.CriteriaBuilder.class);

        jakarta.persistence.criteria.Path<Object> pathTenantId = mock(jakarta.persistence.criteria.Path.class);
        jakarta.persistence.criteria.Predicate predEqTenant = mock(jakarta.persistence.criteria.Predicate.class);
        jakarta.persistence.criteria.Predicate predAnd = mock(jakarta.persistence.criteria.Predicate.class);

        when(root.get("tenantId")).thenReturn(pathTenantId);
        when(builder.equal(pathTenantId, tenantId.value())).thenReturn(predEqTenant);
        when(builder.and(any(jakarta.persistence.criteria.Predicate[].class))).thenReturn(predAnd);

        // Executa a lambda com filter=null — cobre o branch FALSE de "if (filter != null)"
        jakarta.persistence.criteria.Predicate resultPredicate = spec.toPredicate(root, query, builder);
        assertThat(resultPredicate).isEqualTo(predAnd);
    }

    private User createSampleUser() {
        return createSampleUserWith(UserId.random(), TenantId.random());
    }

    private User createSampleUserWith(UserId userId, TenantId tenantId) {
        return User.restore(
            userId,
            tenantId,
            "dev@qsoft.com",
            "Developer",
            HierarchyLevel.COLLABORATOR,
            null,
            "hash",
            UserStatus.ACTIVE,
            Instant.now(),
            Instant.now()
        );
    }
}
