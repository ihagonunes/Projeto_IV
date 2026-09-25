package br.com.qsoft.governance.iam.domain.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PermissionMatrixTest {

    @Test
    void shouldDefinePermissionsForEveryLevel() {
        for (HierarchyLevel level : HierarchyLevel.values()) {
            assertThat(PermissionMatrix.permissionsFor(level))
                .as("nível %s deve ter permissões padrão", level)
                .isNotEmpty();
        }
    }

    @Test
    void levelsShouldBeCumulative() {
        assertThat(PermissionMatrix.permissionsFor(HierarchyLevel.LEADER))
            .containsAll(PermissionMatrix.permissionsFor(HierarchyLevel.COLLABORATOR));
        assertThat(PermissionMatrix.permissionsFor(HierarchyLevel.MANAGER))
            .containsAll(PermissionMatrix.permissionsFor(HierarchyLevel.LEADER));
        assertThat(PermissionMatrix.permissionsFor(HierarchyLevel.EXECUTIVE))
            .containsAll(PermissionMatrix.permissionsFor(HierarchyLevel.MANAGER));
    }

    @Test
    void collaboratorShouldReadAndUseChatButNotManageUsers() {
        assertThat(PermissionMatrix.allows(HierarchyLevel.COLLABORATOR,
            Permission.of(Resource.CHAT, Action.CREATE))).isTrue();
        assertThat(PermissionMatrix.allows(HierarchyLevel.COLLABORATOR,
            Permission.of(Resource.USER, Action.MANAGE))).isFalse();
    }

    @Test
    void leaderShouldReadUsersButNotManageThem() {
        assertThat(PermissionMatrix.allows(HierarchyLevel.LEADER,
            Permission.of(Resource.USER, Action.READ))).isTrue();
        assertThat(PermissionMatrix.allows(HierarchyLevel.LEADER,
            Permission.of(Resource.USER, Action.MANAGE))).isFalse();
    }

    @Test
    void managerShouldManageUsersAndInvites() {
        assertThat(PermissionMatrix.allows(HierarchyLevel.MANAGER,
            Permission.of(Resource.USER, Action.MANAGE))).isTrue();
        assertThat(PermissionMatrix.allows(HierarchyLevel.MANAGER,
            Permission.of(Resource.INVITE, Action.MANAGE))).isTrue();
    }

    @Test
    void executiveShouldManageDepartmentAndRoles() {
        assertThat(PermissionMatrix.allows(HierarchyLevel.EXECUTIVE,
            Permission.of(Resource.DEPARTMENT, Action.MANAGE))).isTrue();
        assertThat(PermissionMatrix.allows(HierarchyLevel.EXECUTIVE,
            Permission.of(Resource.ROLE, Action.MANAGE))).isTrue();
        assertThat(PermissionMatrix.allows(HierarchyLevel.EXECUTIVE,
            Permission.of(Resource.TENANT, Action.READ))).isTrue();
        assertThat(PermissionMatrix.allows(HierarchyLevel.EXECUTIVE,
            Permission.of(Resource.TENANT, Action.MANAGE))).isTrue();
    }

    @Test
    void actionsForResourceShouldReflectTheLevel() {
        assertThat(PermissionMatrix.actionsFor(HierarchyLevel.COLLABORATOR, Resource.CHAT))
            .containsExactlyInAnyOrder(Action.CREATE, Action.READ);
        assertThat(PermissionMatrix.actionsFor(HierarchyLevel.EXECUTIVE, Resource.DEPARTMENT))
            .containsExactlyInAnyOrder(Action.READ, Action.MANAGE);
    }

    @Test
    void adminShouldNotBeAHierarchyLevel() {
        assertThat(Arrays.stream(HierarchyLevel.values()).map(Enum::name))
            .doesNotContain("ADMIN");
    }
}
