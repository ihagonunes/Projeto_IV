package br.com.qsoft.governance.iam.domain.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import org.junit.jupiter.api.Test;

class RoleTest {

    private final TenantId tenantId = TenantId.random();

    @Test
    void shouldCreateRoleWithoutPermissions() {
        Role role = Role.create(RoleId.random(), tenantId, "ADMIN");

        assertThat(role.name()).isEqualTo("ADMIN");
        assertThat(role.tenantId()).isEqualTo(tenantId);
        assertThat(role.permissions()).isEmpty();
    }

    @Test
    void shouldGrantAndRevokePermission() {
        Role role = Role.create(RoleId.random(), tenantId, "MANAGER");
        Permission manageUsers = Permission.of(Resource.USER, Action.MANAGE);

        role.grantPermission(manageUsers);
        assertThat(role.hasPermission(manageUsers)).isTrue();
        assertThat(role.permissions()).containsExactly(manageUsers);

        role.revokePermission(manageUsers);
        assertThat(role.hasPermission(manageUsers)).isFalse();
        assertThat(role.permissions()).isEmpty();
    }

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> Role.create(RoleId.random(), tenantId, "  "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRename() {
        Role role = Role.create(RoleId.random(), tenantId, "OLD");
        role.rename("NEW");
        assertThat(role.name()).isEqualTo("NEW");
    }

    @Test
    void permissionShouldRejectNullParts() {
        assertThatThrownBy(() -> Permission.of(null, Action.READ))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Permission.of(Resource.USER, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void roleAssignmentShouldReferenceDepartmentById() {
        var assignment = RoleAssignment.create(
            br.com.qsoft.governance.sharedkernel.id.UserId.random(),
            RoleId.random(), tenantId, java.util.UUID.randomUUID());

        assertThat(assignment.isDepartmentScoped()).isTrue();

        var tenantWide = RoleAssignment.create(
            br.com.qsoft.governance.sharedkernel.id.UserId.random(),
            RoleId.random(), tenantId, null);
        assertThat(tenantWide.isDepartmentScoped()).isFalse();
    }
}
