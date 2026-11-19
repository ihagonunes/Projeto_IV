package br.com.qsoft.governance.iam.domain.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.ScopeType;
import org.junit.jupiter.api.Test;

class SystemRoleTest {

    @Test
    void permissionCountsMatchTheSpec() {
        assertThat(SystemRole.COLLABORATOR.permissions()).hasSize(5);
        assertThat(SystemRole.LEADER.permissions()).hasSize(7);
        assertThat(SystemRole.MANAGER.permissions()).hasSize(14);
        assertThat(SystemRole.EXECUTIVE.permissions()).hasSize(16);
        assertThat(SystemRole.TENANT_ADMIN.permissions()).hasSize(18);
    }

    @Test
    void ladderIsCumulative() {
        assertThat(SystemRole.LEADER.permissions())
            .containsAll(SystemRole.COLLABORATOR.permissions());
        assertThat(SystemRole.MANAGER.permissions())
            .containsAll(SystemRole.LEADER.permissions());
        assertThat(SystemRole.EXECUTIVE.permissions())
            .containsAll(SystemRole.MANAGER.permissions().stream()
                .filter(p -> p.resource() != Resource.SPENDING_ALERT).toList());
        assertThat(SystemRole.MANAGER.permissions()).anyMatch(p -> p.resource() == Resource.SPENDING_ALERT);
        assertThat(SystemRole.EXECUTIVE.permissions()).noneMatch(p -> p.resource() == Resource.SPENDING_ALERT);
        assertThat(SystemRole.EXECUTIVE.permissions()).anyMatch(p -> p.resource() == Resource.SPENDING_BUDGET);
        assertThat(SystemRole.TENANT_ADMIN.permissions()).noneMatch(p ->
            p.resource() == Resource.SPENDING_BUDGET || p.resource() == Resource.SPENDING_ALERT);
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
