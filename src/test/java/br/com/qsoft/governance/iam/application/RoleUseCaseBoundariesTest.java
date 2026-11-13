package br.com.qsoft.governance.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.application.rbac.GrantAuthority;
import br.com.qsoft.governance.iam.application.rbac.PolicyEvaluator;
import br.com.qsoft.governance.iam.application.rbac.TenantAdminGuard;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.RoleAssignmentAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.RoleAssignmentNotFoundException;
import br.com.qsoft.governance.iam.domain.exception.UserNotFoundException;
import br.com.qsoft.governance.iam.domain.rbac.GrantOrigin;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.iam.domain.rbac.SystemRole;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoleUseCaseBoundariesTest {
    private final TenantId tenant = TenantId.random();
    private final UserId actor = UserId.random();
    private final UserId target = UserId.random();
    private final UUID department = UUID.randomUUID();
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final RoleAssignmentRepository assignments = mock(RoleAssignmentRepository.class);
    private final GrantAuthority authority = mock(GrantAuthority.class);
    private final TenantAdminGuard guard = mock(TenantAdminGuard.class);
    private final AssignRole assign = new AssignRole(users, roles, assignments, authority);
    private final RevokeRoleAssignment revoke = new RevokeRoleAssignment(assignments, guard);

    @Test
    void assignmentRejectsUnknownRolesMissingDepartmentAndDeniedDelegation() {
        assertThatThrownBy(() -> assign.assign(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> assign.assign(command("UNKNOWN", null))).hasMessageContaining("roleKey");
        assertThatThrownBy(() -> assign.assign(command("MANAGER", null))).hasMessageContaining("departmentId");
        assertThatThrownBy(() -> assign.assign(command("MANAGER", department)))
            .isInstanceOf(AuthorizationDeniedException.class);
        verifyNoInteractions(users, roles, assignments);
    }

    @Test
    void assignmentRequiresSameTenantUserAndProvisionedRole() {
        when(authority.canGrantDirect(tenant, actor, SystemRole.MANAGER, department)).thenReturn(true);
        assertThatThrownBy(() -> assign.assign(command("MANAGER", department))).isInstanceOf(UserNotFoundException.class);
        when(users.findByTenantIdAndId(tenant, target)).thenReturn(Optional.of(user()));
        assertThatThrownBy(() -> assign.assign(command("MANAGER", department)))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("ausente");
        verifyNoInteractions(assignments);
    }

    @Test
    void sameRoleCanBeGrantedInAnotherDepartmentButNotTwiceInOneDepartment() {
        Role manager = role(tenant, "MANAGER");
        Role otherRole = role(tenant, "LEADER");
        when(authority.canGrantDirect(tenant, actor, SystemRole.MANAGER, department)).thenReturn(true);
        when(users.findByTenantIdAndId(tenant, target)).thenReturn(Optional.of(user()));
        when(roles.findByTenantIdAndName(tenant, "MANAGER")).thenReturn(Optional.of(manager));
        when(assignments.findActiveByTenantIdAndUserId(tenant, target)).thenReturn(
            List.of(grant(target, otherRole, department), grant(target, manager, UUID.randomUUID())));
        when(assignments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RoleAssignment saved = assign.assign(command("MANAGER", department));
        assertThat(saved.departmentId()).isEqualTo(department);
        when(assignments.findActiveByTenantIdAndUserId(tenant, target)).thenReturn(List.of(saved));
        assertThatThrownBy(() -> assign.assign(command("MANAGER", department)))
            .isInstanceOf(RoleAssignmentAlreadyExistsException.class);
    }

    @Test
    void tenantScopedGrantDoesNotPersistDepartmentFromCommand() {
        Role executive = role(tenant, "EXECUTIVE");
        when(authority.canGrantDirect(tenant, actor, SystemRole.EXECUTIVE, department)).thenReturn(true);
        when(users.findByTenantIdAndId(tenant, target)).thenReturn(Optional.of(user()));
        when(roles.findByTenantIdAndName(tenant, "EXECUTIVE")).thenReturn(Optional.of(executive));
        when(assignments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RoleAssignment saved = assign.assign(command("EXECUTIVE", department));

        assertThat(saved.scopeType()).isEqualTo(br.com.qsoft.governance.sharedkernel.rbac.ScopeType.TENANT);
        assertThat(saved.departmentId()).isNull();
        assertThat(saved.userId()).isEqualTo(target);
    }

    @Test
    void revocationHidesOtherTenantWrongUserAndInactiveGrants() {
        assertThatThrownBy(() -> revoke.revoke(null)).isInstanceOf(IllegalArgumentException.class);
        Role local = role(tenant, "COLLABORATOR");
        for (RoleAssignment unavailable : List.of(
            grant(target, role(TenantId.random(), "COLLABORATOR"), null),
            grant(actor, local, null),
            grant(target, local, null).revoke(actor, Instant.now()))) {
            when(assignments.findById(unavailable.id())).thenReturn(Optional.of(unavailable));
            assertThatThrownBy(() -> revoke.revoke(new RevokeRoleAssignmentCommand(
                tenant, actor, target, unavailable.id()))).isInstanceOf(RoleAssignmentNotFoundException.class);
        }
        verifyNoInteractions(guard);
        verify(assignments, never()).save(any());
    }

    @Test
    void actorCannotRevokeOwnGrantAfterAdminGuard() {
        RoleAssignment own = grant(actor, role(tenant, "COLLABORATOR"), null);
        when(assignments.findById(own.id())).thenReturn(Optional.of(own));

        assertThatThrownBy(() -> revoke.revoke(new RevokeRoleAssignmentCommand(tenant, actor, actor, own.id())))
            .isInstanceOf(AuthorizationDeniedException.class);
        verify(guard).ensureCanRemoveTenantAdmin(tenant, actor);
        verify(assignments, never()).save(any());
    }

    @Test
    void listingRolesAndBootstrapFailWhenRequiredTenantDataIsMissing() {
        assertThatThrownBy(() -> new ListRoles(users, authority).list(tenant, actor))
            .isInstanceOf(UserNotFoundException.class);
        var provision = mock(ProvisionUser.class);
        when(provision.provision(any())).thenReturn(user());
        assertThatThrownBy(() -> new BootstrapTenantAdmin(provision, roles, assignments)
            .bootstrap(tenant, "Admin", "admin@example.com", "test-password"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("TENANT_ADMIN");
        verifyNoInteractions(authority, assignments);
    }

    @Test
    void accessViewExcludesMissingAndForeignRoles() {
        Role missing = role(tenant, "MANAGER");
        Role foreign = role(TenantId.random(), "COLLABORATOR");
        Role local = role(tenant, "COLLABORATOR");
        when(roles.findById(foreign.id())).thenReturn(Optional.of(foreign));
        when(roles.findById(local.id())).thenReturn(Optional.of(local));
        when(assignments.findActiveByTenantIdAndUserId(tenant, target))
            .thenReturn(List.of(grant(target, missing, department), grant(target, foreign, null),
                grant(target, local, null)));

        var access = new GetUserAccess(assignments, roles, mock(PolicyEvaluator.class)).forUser(tenant, target);

        assertThat(access.concessions()).singleElement().satisfies(view ->
            assertThat(view.role()).isEqualTo("COLLABORATOR"));
    }

    private AssignRoleCommand command(String key, UUID departmentId) {
        return new AssignRoleCommand(tenant, actor, target, key, departmentId, GrantOrigin.MIGRATION);
    }

    private User user() {
        return User.create(target, tenant, "dev@example.com", "Pessoa", HierarchyLevel.COLLABORATOR, department);
    }

    private static Role role(TenantId tenant, String key) {
        return Role.create(RoleId.random(), tenant, key);
    }

    private RoleAssignment grant(UserId user, Role role, UUID departmentId) {
        return RoleAssignment.grant(user, role, departmentId, GrantOrigin.MIGRATION, actor, Instant.EPOCH);
    }
}
