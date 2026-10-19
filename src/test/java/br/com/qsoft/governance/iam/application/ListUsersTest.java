package br.com.qsoft.governance.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.application.port.UserSearchRepository;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Listagem de usuários com o recorte de {@code visibilityFor(USER:READ)}
 * (SPEC-01 §6).
 */
class ListUsersTest {

    private final List<User> users = new ArrayList<>();
    private final AuthorizationQuery authorizationQuery = mock(AuthorizationQuery.class);
    private final UserId actor = UserId.random();

    @BeforeEach
    void defaultVisibility() {
        when(authorizationQuery.visibilityFor(any(), any(), any(), any()))
            .thenReturn(new VisibilityScope.TenantWide());
    }

    /** Fake em memória com os mesmos filtros do adapter (inclui a visibilidade). */
    private final UserSearchRepository userSearchRepository = (tenantId, filter, page, size) -> {
        List<User> matched = users.stream()
            .filter(user -> user.tenantId().equals(tenantId))
            .filter(user -> {
                UUID visibleUser = filter.visibleUserId();
                Set<UUID> visibleDepartments = filter.visibleDepartmentIds();
                if (visibleUser == null && visibleDepartments.isEmpty()) {
                    return true;
                }
                if (!visibleDepartments.isEmpty()) {
                    return (user.departmentId() != null
                        && visibleDepartments.contains(user.departmentId()))
                        || user.id().value().equals(visibleUser);
                }
                return user.id().value().equals(visibleUser);
            })
            .filter(user -> filter.hierarchyLevel() == null
                || user.hierarchyLevel() == filter.hierarchyLevel())
            .filter(user -> filter.departmentId() == null
                || filter.departmentId().equals(user.departmentId()))
            .filter(user -> !hasText(filter.name())
                || user.name().toLowerCase().contains(filter.name().trim().toLowerCase()))
            .filter(user -> !hasText(filter.email())
                || user.email().toLowerCase().contains(filter.email().trim().toLowerCase()))
            .toList();
        int from = Math.min(page * size, matched.size());
        int to = Math.min(from + size, matched.size());
        return new PageResult<>(matched.subList(from, to), matched.size(), page, size);
    };

    private final ListUsers useCase = new ListUsers(userSearchRepository, authorizationQuery);

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private User userIn(TenantId tenantId, String email, String name, HierarchyLevel level,
                        UUID departmentId) {
        User user = User.create(UserId.random(), tenantId, email, name, level, departmentId);
        users.add(user);
        return user;
    }

    private ListUsersCommand command(TenantId tenantId, HierarchyLevel level, UUID department,
                                     String name, String email, int page, int size) {
        return new ListUsersCommand(tenantId, actor, level, department, name, email, page, size);
    }

    @Test
    void shouldReturnOnlyUsersOfTheTenant() {
        TenantId tenantId = TenantId.random();
        userIn(tenantId, "ana@x.com", "Ana", HierarchyLevel.COLLABORATOR, null);
        userIn(TenantId.random(), "outro@y.com", "Outro", HierarchyLevel.COLLABORATOR, null);

        PageResult<User> result = useCase.list(command(tenantId, null, null, null, null, 0, 20));

        assertThat(result.content()).extracting(User::email).containsExactly("ana@x.com");
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void shouldFilterByHierarchyLevelAndDepartment() {
        TenantId tenantId = TenantId.random();
        UUID marketing = UUID.randomUUID();
        userIn(tenantId, "gestor@x.com", "Gestor", HierarchyLevel.MANAGER, marketing);
        userIn(tenantId, "colab@x.com", "Colab", HierarchyLevel.COLLABORATOR, marketing);
        userIn(tenantId, "outro@x.com", "Outro", HierarchyLevel.MANAGER, UUID.randomUUID());

        PageResult<User> result = useCase.list(
            command(tenantId, HierarchyLevel.MANAGER, marketing, null, null, 0, 20));

        assertThat(result.content()).extracting(User::email).containsExactly("gestor@x.com");
    }

    @Test
    void shouldFilterByNameAndEmail() {
        TenantId tenantId = TenantId.random();
        userIn(tenantId, "ana.silva@x.com", "Ana Silva", HierarchyLevel.COLLABORATOR, null);
        userIn(tenantId, "bruno@x.com", "Bruno", HierarchyLevel.COLLABORATOR, null);

        PageResult<User> byName = useCase.list(command(tenantId, null, null, "silva", null, 0, 20));
        PageResult<User> byEmail = useCase.list(command(tenantId, null, null, null, "bruno@", 0, 20));

        assertThat(byName.content()).extracting(User::email).containsExactly("ana.silva@x.com");
        assertThat(byEmail.content()).extracting(User::email).containsExactly("bruno@x.com");
    }

    @Test
    void shouldPaginate() {
        TenantId tenantId = TenantId.random();
        for (int i = 0; i < 5; i++) {
            userIn(tenantId, "u" + i + "@x.com", "User " + i, HierarchyLevel.COLLABORATOR, null);
        }

        PageResult<User> first = useCase.list(command(tenantId, null, null, null, null, 0, 2));
        PageResult<User> second = useCase.list(command(tenantId, null, null, null, null, 1, 2));

        assertThat(first.content()).hasSize(2);
        assertThat(first.totalElements()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(second.content()).hasSize(2);
        assertThat(second.content()).doesNotContainAnyElementsOf(first.content());
    }

    @Test
    void shouldClampSizeToMaximum() {
        TenantId tenantId = TenantId.random();

        PageResult<User> result = useCase.list(command(tenantId, null, null, null, null, 0, 9999));

        assertThat(result.size()).isEqualTo(100);
    }

    @Test
    void shouldRejectNullCommand() {
        assertThatThrownBy(() -> useCase.list(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void departmentsVisibilityRestrictsToDepartmentsOrSelf() {
        TenantId tenantId = TenantId.random();
        UUID marketing = UUID.randomUUID();
        User self = userIn(tenantId, "self@x.com", "Self", HierarchyLevel.MANAGER, null);
        userIn(tenantId, "in@x.com", "In", HierarchyLevel.COLLABORATOR, marketing);
        userIn(tenantId, "out@x.com", "Out", HierarchyLevel.COLLABORATOR, UUID.randomUUID());
        when(authorizationQuery.visibilityFor(tenantId, actor, Resource.USER, Action.READ))
            .thenReturn(new VisibilityScope.Departments(Set.of(marketing), self.id()));

        PageResult<User> result = useCase.list(command(tenantId, null, null, null, null, 0, 20));

        assertThat(result.content()).extracting(User::email)
            .containsExactlyInAnyOrder("self@x.com", "in@x.com");
    }

    @Test
    void selfOnlyVisibilityRestrictsToTheActor() {
        TenantId tenantId = TenantId.random();
        userIn(tenantId, "other@x.com", "Other", HierarchyLevel.COLLABORATOR, null);
        when(authorizationQuery.visibilityFor(tenantId, actor, Resource.USER, Action.READ))
            .thenReturn(new VisibilityScope.SelfOnly(actor));

        PageResult<User> result = useCase.list(command(tenantId, null, null, null, null, 0, 20));

        assertThat(result.content()).extracting(User::email).isEmpty();
    }

    @Test
    void noneVisibilityReturnsEmptyPage() {
        TenantId tenantId = TenantId.random();
        when(authorizationQuery.visibilityFor(tenantId, actor, Resource.USER, Action.READ))
            .thenReturn(new VisibilityScope.None());

        PageResult<User> result = useCase.list(command(tenantId, null, null, null, null, 0, 20));

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    void departmentFilterOutsideVisibilityIsForbidden() {
        TenantId tenantId = TenantId.random();
        UUID visible = UUID.randomUUID();
        when(authorizationQuery.visibilityFor(tenantId, actor, Resource.USER, Action.READ))
            .thenReturn(new VisibilityScope.Departments(Set.of(visible), actor));

        assertThatThrownBy(() -> useCase.list(
            command(tenantId, null, UUID.randomUUID(), null, null, 0, 20)))
            .isInstanceOf(AuthorizationDeniedException.class);
    }
}
