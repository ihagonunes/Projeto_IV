package br.com.qsoft.governance.iam.application.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.LastTenantAdminException;
import br.com.qsoft.governance.iam.domain.rbac.GrantOrigin;
import br.com.qsoft.governance.iam.domain.rbac.Permission;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.iam.domain.rbac.SystemRole;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.RoleAssignmentId;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.ScopeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GrantAndDominanceTest {

    private final TenantId tenant = TenantId.random();
    private final UserId actor = UserId.random();
    private final UserId target = UserId.random();
    private final UUID department = UUID.randomUUID();
    private final UUID outside = UUID.randomUUID();
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final RoleAssignmentRepository assignments = mock(RoleAssignmentRepository.class);
    private final GrantAuthority authority = new GrantAuthority(users, roles, assignments);
    private final DominancePolicy dominance = new DominancePolicy(users, roles, assignments);
    private final TenantAdminGuard guard = new TenantAdminGuard(roles, assignments);

    @Test
    void managerCanInviteLowerRolesOnlyInCoveredDepartmentAndCannotGrantDirectly() {
        active(actor, department);
        Role manager = role(tenant, "MANAGER");
        manager.grantPermission(Permission.of(Resource.INVITE, Action.MANAGE));
        grants(actor, grant(actor, manager, ScopeType.DEPARTMENT, department));

        assertThat(authority.canGrantByInvite(tenant, actor, SystemRole.COLLABORATOR, department)).isTrue();
        assertThat(authority.canGrantByInvite(tenant, actor, SystemRole.LEADER, outside)).isFalse();
        assertThat(authority.canGrantByInvite(tenant, actor, SystemRole.MANAGER, department)).isFalse();
        assertThat(authority.canGrantDirect(tenant, actor, SystemRole.LEADER, department)).isFalse();
    }

    @Test
    void grantsRejectMissingInactiveAndForeignActorsBeforeReadingAssignments() {
        assertThat(authority.canGrantByInvite(tenant, actor, SystemRole.LEADER, department)).isFalse();
        active(actor, department).deactivate();
        assertThat(authority.canGrantByInvite(tenant, actor, SystemRole.LEADER, department)).isFalse();
        when(users.findByTenantIdAndId(tenant, actor))
            .thenReturn(Optional.of(user(TenantId.random(), actor, department)));
        assertThat(authority.canGrantByInvite(tenant, actor, SystemRole.LEADER, department)).isFalse();
        verifyNoInteractions(assignments);
    }

    @Test
    void ignoresMissingForeignCustomAndEqualRankGrants() {
        active(actor, department);
        active(target, department);
        Role missing = Role.create(RoleId.random(), tenant, "MANAGER");
        Role foreign = role(TenantId.random(), "TENANT_ADMIN");
        Role custom = role(tenant, "CUSTOM");
        Role leader = role(tenant, "LEADER");
        leader.grantPermission(Permission.of(Resource.INVITE, Action.MANAGE));
        grants(actor, grant(actor, missing, ScopeType.DEPARTMENT, department),
            grant(actor, foreign, ScopeType.TENANT, null), grant(actor, custom, ScopeType.TENANT, null),
            grant(actor, leader, ScopeType.DEPARTMENT, outside));
        grants(target, grant(target, leader, ScopeType.DEPARTMENT, department));

        assertThat(authority.canGrantByInvite(tenant, actor, SystemRole.LEADER, department)).isFalse();
        assertThat(dominance.dominates(tenant, actor, target)).isFalse();
        assertThat(dominance.coversDepartment(tenant, actor, department)).isFalse();
    }

    @Test
    void adminCanGrantAnyRoleAndDominateOtherUsersButNotSelf() {
        active(actor, department);
        active(target, outside);
        Role admin = role(tenant, "TENANT_ADMIN");
        grants(actor, grant(actor, admin, ScopeType.TENANT, null));

        assertThat(authority.canGrantDirect(tenant, actor, SystemRole.TENANT_ADMIN, null)).isTrue();
        assertThat(dominance.dominates(tenant, actor, target)).isTrue();
        assertThat(dominance.dominates(tenant, actor, actor)).isFalse();
        assertThat(dominance.coversDepartment(tenant, actor, outside)).isTrue();
        assertThat(dominance.coversDepartment(tenant, actor, null)).isFalse();
    }

    @Test
    void dominanceRequiresBothCoverageOfEveryTargetGrantAndCoverageOfTargetDepartment() {
        active(actor, department);
        User employee = active(target, department);
        Role manager = role(tenant, "MANAGER");
        Role leader = role(tenant, "LEADER");
        Role collaborator = role(tenant, "COLLABORATOR");
        Role executive = role(tenant, "EXECUTIVE");
        grants(actor, grant(actor, manager, ScopeType.DEPARTMENT, department));
        RoleAssignment self = grant(target, collaborator, ScopeType.SELF, null);
        RoleAssignment local = grant(target, leader, ScopeType.DEPARTMENT, department);

        grants(target, self, local);
        assertThat(dominance.dominates(tenant, actor, target)).isTrue();
        assertThat(dominance.coversDepartment(tenant, actor, department)).isTrue();
        grants(target, self, grant(target, leader, ScopeType.DEPARTMENT, outside));
        assertThat(dominance.dominates(tenant, actor, target)).isFalse();
        grants(target, local, grant(target, executive, ScopeType.TENANT, null));
        assertThat(dominance.dominates(tenant, actor, target)).isFalse();
        grants(target, local);
        employee.changeDepartment(outside);
        assertThat(dominance.dominates(tenant, actor, target)).isFalse();
    }

    @Test
    void dominanceRejectsMissingOrInactiveActorAndMissingTarget() {
        assertThat(dominance.dominates(tenant, actor, target)).isFalse();
        active(actor, department);
        assertThat(dominance.dominates(tenant, actor, target)).isFalse();
        active(target, department);
        active(actor, department).deactivate();
        assertThat(dominance.dominates(tenant, actor, target)).isFalse();
        verifyNoInteractions(assignments);
    }

    @Test
    void tenantScopeContainsAnyRoleButSelfScopeNeverCoversDepartment() {
        RoleAssignment tenantGrant = grant(actor, role(tenant, "EXECUTIVE"), ScopeType.TENANT, null);
        RoleAssignment selfGrant = grant(actor, role(tenant, "COLLABORATOR"), ScopeType.SELF, null);
        RoleAssignment departmentGrant = grant(actor, role(tenant, "MANAGER"), ScopeType.DEPARTMENT, department);

        assertThat(ScopeCoverage.contains(tenantGrant, SystemRole.TENANT_ADMIN, null)).isTrue();
        assertThat(ScopeCoverage.contains(selfGrant, SystemRole.COLLABORATOR, department)).isFalse();
        assertThat(ScopeCoverage.contains(departmentGrant, SystemRole.EXECUTIVE, department)).isFalse();
        assertThat(ScopeCoverage.coversDepartment(tenantGrant, outside)).isTrue();
        assertThat(ScopeCoverage.coversDepartment(selfGrant, department)).isFalse();
    }

    @Test
    void adminGuardLocksRoleBeforeCountingAndRejectsRemovalOfLastAdmin() {
        Role admin = role(tenant, "TENANT_ADMIN");
        when(roles.findByTenantIdAndNameForUpdate(tenant, "TENANT_ADMIN")).thenReturn(Optional.of(admin));
        grants(target, grant(target, admin, ScopeType.TENANT, null));
        when(assignments.countActiveByTenantIdAndRoleId(tenant, admin.id())).thenReturn(1L, 2L);

        assertThatThrownBy(() -> guard.ensureCanRemoveTenantAdmin(tenant, target))
            .isInstanceOf(LastTenantAdminException.class);
        var order = inOrder(roles, assignments);
        order.verify(roles).findByTenantIdAndNameForUpdate(tenant, "TENANT_ADMIN");
        order.verify(assignments).countActiveByTenantIdAndRoleId(tenant, admin.id());
        guard.ensureCanRemoveTenantAdmin(tenant, target);
    }

    @Test
    void adminGuardFailsWhenSystemRoleIsMissing() {
        assertThatThrownBy(() -> guard.ensureCanRemoveTenantAdmin(tenant, target))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("TENANT_ADMIN");
        verifyNoInteractions(assignments);
    }

    private User active(UserId id, UUID departmentId) {
        User user = user(tenant, id, departmentId);
        when(users.findByTenantIdAndId(tenant, id)).thenReturn(Optional.of(user));
        return user;
    }

    private static User user(TenantId tenant, UserId id, UUID departmentId) {
        return User.create(id, tenant, id + "@example.com", "Pessoa", HierarchyLevel.MANAGER, departmentId);
    }

    private Role role(TenantId roleTenant, String key) {
        Role role = Role.create(RoleId.random(), roleTenant, key);
        when(roles.findById(role.id())).thenReturn(Optional.of(role));
        return role;
    }

    private RoleAssignment grant(UserId user, Role role, ScopeType scope, UUID departmentId) {
        return new RoleAssignment(RoleAssignmentId.random(), user, role.id(), tenant, scope, departmentId,
            GrantOrigin.MIGRATION, null, Instant.EPOCH, null, null);
    }

    private void grants(UserId user, RoleAssignment... values) {
        when(assignments.findActiveByTenantIdAndUserId(tenant, user)).thenReturn(List.of(values));
    }
}
