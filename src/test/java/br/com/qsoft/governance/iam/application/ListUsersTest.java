package br.com.qsoft.governance.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.iam.application.port.UserSearchRepository;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListUsersTest {

    private final List<User> users = new ArrayList<>();

    /**
     * Fake em memória da porta de busca: aplica os MESMOS filtros do adapter
     * (tenant + hierarquia + departamento + nome + e-mail) e pagina o resultado.
     */
    private final UserSearchRepository userSearchRepository = (tenantId, filter, page, size) -> {
        List<User> matched = users.stream()
            .filter(user -> user.tenantId().equals(tenantId))
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

    private final ListUsers useCase = new ListUsers(userSearchRepository);

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private User userIn(TenantId tenantId, String email, String name, HierarchyLevel level,
                        UUID departmentId) {
        User user = User.create(UserId.random(), tenantId, email, name, level, departmentId);
        users.add(user);
        return user;
    }

    @Test
    void shouldReturnOnlyUsersOfTheTenant() {
        TenantId tenantId = TenantId.random();
        userIn(tenantId, "ana@x.com", "Ana", HierarchyLevel.COLLABORATOR, null);
        userIn(TenantId.random(), "outro@y.com", "Outro", HierarchyLevel.COLLABORATOR, null);

        PageResult<User> result = useCase.list(new ListUsersCommand(
            tenantId, null, null, null, null, 0, 20));

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

        PageResult<User> result = useCase.list(new ListUsersCommand(
            tenantId, HierarchyLevel.MANAGER, marketing, null, null, 0, 20));

        assertThat(result.content()).extracting(User::email).containsExactly("gestor@x.com");
    }

    @Test
    void shouldFilterByNameAndEmail() {
        TenantId tenantId = TenantId.random();
        userIn(tenantId, "ana.silva@x.com", "Ana Silva", HierarchyLevel.COLLABORATOR, null);
        userIn(tenantId, "bruno@x.com", "Bruno", HierarchyLevel.COLLABORATOR, null);

        PageResult<User> byName = useCase.list(new ListUsersCommand(
            tenantId, null, null, "silva", null, 0, 20));
        PageResult<User> byEmail = useCase.list(new ListUsersCommand(
            tenantId, null, null, null, "bruno@", 0, 20));

        assertThat(byName.content()).extracting(User::email).containsExactly("ana.silva@x.com");
        assertThat(byEmail.content()).extracting(User::email).containsExactly("bruno@x.com");
    }

    @Test
    void shouldPaginate() {
        TenantId tenantId = TenantId.random();
        for (int i = 0; i < 5; i++) {
            userIn(tenantId, "u" + i + "@x.com", "User " + i, HierarchyLevel.COLLABORATOR, null);
        }

        PageResult<User> first = useCase.list(new ListUsersCommand(
            tenantId, null, null, null, null, 0, 2));
        PageResult<User> second = useCase.list(new ListUsersCommand(
            tenantId, null, null, null, null, 1, 2));

        assertThat(first.content()).hasSize(2);
        assertThat(first.totalElements()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(second.content()).hasSize(2);
        assertThat(second.content()).doesNotContainAnyElementsOf(first.content());
    }

    @Test
    void shouldClampSizeToMaximum() {
        TenantId tenantId = TenantId.random();

        PageResult<User> result = useCase.list(new ListUsersCommand(
            tenantId, null, null, null, null, 0, 9999));

        assertThat(result.size()).isEqualTo(100);
    }

    @Test
    void shouldRejectNullCommand() {
        assertThatThrownBy(() -> useCase.list(null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
