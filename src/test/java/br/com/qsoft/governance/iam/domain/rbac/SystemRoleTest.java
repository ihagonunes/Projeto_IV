package br.com.qsoft.governance.iam.domain.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.ScopeType;
import org.junit.jupiter.api.Test;

class SystemRoleTest {

    @Test
    void permissionCountsMatchTheSpec() {
        assertThat(SystemRole.COLLABORATOR.permissions()).hasSize(7);
        assertThat(SystemRole.LEADER.permissions()).hasSize(10);
        assertThat(SystemRole.MANAGER.permissions()).hasSize(17);
        assertThat(SystemRole.EXECUTIVE.permissions()).hasSize(18);
        assertThat(SystemRole.TENANT_ADMIN.permissions()).hasSize(21);
    }

    @Test
    void ladderIsCumulative() {
        assertThat(SystemRole.LEADER.permissions())
            .containsAll(SystemRole.COLLABORATOR.permissions());
        assertThat(SystemRole.MANAGER.permissions())
            .containsAll(SystemRole.LEADER.permissions());
        assertThat(SystemRole.EXECUTIVE.permissions())
            .containsAll(SystemRole.MANAGER.permissions().stream()
                .filter(p -> p.resource() != Resource.SPENDING_ALERT
                    && p.resource() != Resource.AGENT).toList());
        assertThat(SystemRole.MANAGER.permissions()).anyMatch(p -> p.resource() == Resource.SPENDING_ALERT);
        assertThat(SystemRole.EXECUTIVE.permissions()).noneMatch(p -> p.resource() == Resource.SPENDING_ALERT);
        assertThat(SystemRole.EXECUTIVE.permissions()).anyMatch(p -> p.resource() == Resource.SPENDING_BUDGET);
        assertThat(SystemRole.TENANT_ADMIN.permissions()).noneMatch(p ->
            p.resource() == Resource.SPENDING_BUDGET || p.resource() == Resource.SPENDING_ALERT);

        assertThat(SystemRole.EXECUTIVE.permissions())
            .contains(SystemRole.MANAGER.permissions().stream()
                .filter(p -> p.resource() == Resource.AGENT
                    && p.action() != Action.MANAGE)
                .toArray(Permission[]::new));
    }

    @Test
    void agentPermissionsFollowTheDecidedMatrix() {
        Permission read = Permission.of(Resource.AGENT, Action.READ);
        Permission manage = Permission.of(Resource.AGENT, Action.MANAGE);
        Permission run = Permission.of(Resource.AGENT, Action.RUN);

        assertThat(SystemRole.COLLABORATOR.permissions()).contains(read, run).doesNotContain(manage);
        assertThat(SystemRole.LEADER.permissions()).contains(read, manage, run);
        assertThat(SystemRole.MANAGER.permissions()).contains(read, manage, run);
        assertThat(SystemRole.EXECUTIVE.permissions()).contains(read, run).doesNotContain(manage);
        assertThat(SystemRole.TENANT_ADMIN.permissions()).contains(read, manage, run);
    }

    @Test
    void agentManageDoesNotImplyAgentRead() {
        Role role = Role.create(RoleId.random(), TenantId.random(), "CUSTOM_ONLY_MANAGE")
            .grantPermission(Permission.of(Resource.AGENT, Action.MANAGE));

        assertThat(role.hasPermission(Permission.of(Resource.AGENT, Action.READ))).isFalse();
        assertThat(role.hasPermission(Permission.of(Resource.AGENT, Action.RUN))).isFalse();
    }

    @Test
    void tenantAdminHasNoChatPermission() {
        assertThat(SystemRole.TENANT_ADMIN.permissions())
            .noneMatch(permission -> permission.resource() == Resource.CHAT
                || permission.resource() == Resource.USAGE || permission.resource() == Resource.GOVERNANCE_SCORE);
    }

    @Test
    void scopesAndRanksAreFixed() {
        assertThat(SystemRole.COLLABORATOR.scope()).isEqualTo(ScopeType.SELF);
        assertThat(SystemRole.LEADER.scope()).isEqualTo(ScopeType.DEPARTMENT);
        assertThat(SystemRole.MANAGER.scope()).isEqualTo(ScopeType.DEPARTMENT);
        assertThat(SystemRole.EXECUTIVE.scope()).isEqualTo(ScopeType.TENANT);
        assertThat(SystemRole.TENANT_ADMIN.scope()).isEqualTo(ScopeType.TENANT);

        assertThat(SystemRole.COLLABORATOR.rank()).isEqualTo(1);
        assertThat(SystemRole.LEADER.rank()).isEqualTo(2);
        assertThat(SystemRole.MANAGER.rank()).isEqualTo(3);
        assertThat(SystemRole.EXECUTIVE.rank()).isEqualTo(4);
        assertThat(SystemRole.TENANT_ADMIN.rank()).isEqualTo(100);
    }

    @Test
    void fromKeyResolvesByNameAndRejectsUnknown() {
        assertThat(SystemRole.fromKey("MANAGER")).contains(SystemRole.MANAGER);
        assertThat(SystemRole.fromKey("  LEADER  ")).contains(SystemRole.LEADER);
        assertThat(SystemRole.fromKey("ADMIN")).isEmpty();
        assertThat(SystemRole.fromKey(null)).isEmpty();
        assertThat(SystemRole.fromKey("  ")).isEmpty();
    }

    @Test
    void onlyCollaboratorAndLeaderAreDelegableByNonAdmins() {
        assertThat(SystemRole.COLLABORATOR.isDelegableByNonAdmins()).isTrue();
        assertThat(SystemRole.LEADER.isDelegableByNonAdmins()).isTrue();
        assertThat(SystemRole.MANAGER.isDelegableByNonAdmins()).isFalse();
        assertThat(SystemRole.EXECUTIVE.isDelegableByNonAdmins()).isFalse();
        assertThat(SystemRole.TENANT_ADMIN.isDelegableByNonAdmins()).isFalse();
    }
}
