package br.com.qsoft.governance.iam.domain.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.sharedkernel.id.RoleAssignmentId;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.AccessTarget;
import br.com.qsoft.governance.sharedkernel.rbac.ScopeType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoleAssignmentTest {

    private final TenantId tenantId = TenantId.random();
    private final UserId actor = UserId.random();

    private Role role(String name) {
        return Role.create(RoleId.random(), tenantId, name);
    }

    @Test
    void selfScopeCoversOnlyOwnedTargetsOfTheActor() {
        UserId self = UserId.random();
        RoleAssignment assignment = RoleAssignment.grant(
            self, role("COLLABORATOR"), null, GrantOrigin.INVITE, null, Instant.now());

        assertThat(assignment.covers(new AccessTarget.OwnedTarget(self, UUID.randomUUID()))).isTrue();
        assertThat(assignment.covers(new AccessTarget.OwnedTarget(actor, null))).isFalse();
        assertThat(assignment.covers(new AccessTarget.DepartmentTarget(UUID.randomUUID()))).isFalse();
        assertThat(assignment.covers(new AccessTarget.TenantTarget())).isFalse();
    }

    @Test
    void departmentScopeCoversDepartmentAndOwnedTargetsOfThatDepartment() {
        UUID department = UUID.randomUUID();
        RoleAssignment assignment = RoleAssignment.grant(
            actor, role("MANAGER"), department, GrantOrigin.INVITE, null, Instant.now());

        assertThat(assignment.covers(new AccessTarget.DepartmentTarget(department))).isTrue();
        assertThat(assignment.covers(new AccessTarget.OwnedTarget(UserId.random(), department))).isTrue();
        assertThat(assignment.covers(new AccessTarget.DepartmentTarget(UUID.randomUUID()))).isFalse();
        assertThat(assignment.covers(new AccessTarget.OwnedTarget(UserId.random(), UUID.randomUUID())))
            .isFalse();
        assertThat(assignment.covers(new AccessTarget.OwnedTarget(UserId.random(), null))).isFalse();
        assertThat(assignment.covers(new AccessTarget.TenantTarget())).isFalse();
    }

    @Test
    void tenantScopeCoversEveryTarget() {
        RoleAssignment assignment = RoleAssignment.grant(
            actor, role("EXECUTIVE"), null, GrantOrigin.DIRECT, null, Instant.now());

        assertThat(assignment.covers(new AccessTarget.TenantTarget())).isTrue();
        assertThat(assignment.covers(new AccessTarget.DepartmentTarget(UUID.randomUUID()))).isTrue();
        assertThat(assignment.covers(new AccessTarget.OwnedTarget(UserId.random(), null))).isTrue();
    }

    @Test
    void grantUsesFixedScopeAndDiscardsDepartmentWhenNotDepartmental() {
        RoleAssignment assignment = RoleAssignment.grant(
            actor, role("COLLABORATOR"), UUID.randomUUID(), GrantOrigin.INVITE, null, Instant.now());

        assertThat(assignment.scopeType()).isEqualTo(ScopeType.SELF);
        assertThat(assignment.departmentId()).isNull();
        assertThat(assignment.isActive()).isTrue();
        assertThat(assignment.origin()).isEqualTo(GrantOrigin.INVITE);
    }

    @Test
    void grantRequiresDepartmentForDepartmentalScope() {
        assertThatThrownBy(() -> RoleAssignment.grant(
            actor, role("MANAGER"), null, GrantOrigin.INVITE, null, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void grantRejectsRoleWithoutSystemScope() {
        assertThatThrownBy(() -> RoleAssignment.grant(
            actor, role("ADMIN"), null, GrantOrigin.DIRECT, null, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void departmentIdMustBePresentIfAndOnlyIfScopeIsDepartment() {
        assertThatThrownBy(() -> new RoleAssignment(
            RoleAssignmentId.random(), actor, RoleId.random(), tenantId, ScopeType.SELF,
            UUID.randomUUID(), GrantOrigin.DIRECT, null, Instant.now(), null, null))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new RoleAssignment(
            RoleAssignmentId.random(), actor, RoleId.random(), tenantId, ScopeType.DEPARTMENT,
            null, GrantOrigin.DIRECT, null, Instant.now(), null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revokeIsIdempotentAndMarksInactive() {
        RoleAssignment assignment = RoleAssignment.grant(
            actor, role("EXECUTIVE"), null, GrantOrigin.DIRECT, null, Instant.now());

        RoleAssignment revoked = assignment.revoke(actor, Instant.now());
        assertThat(revoked.isActive()).isFalse();
        assertThat(revoked.revokedBy()).isEqualTo(actor);

        RoleAssignment again = revoked.revoke(actor, Instant.now().plusSeconds(60));
        assertThat(again.revokedAt()).isEqualTo(revoked.revokedAt());
        assertThat(again.revokedBy()).isEqualTo(revoked.revokedBy());
    }

    @Test
    void revocationRequiresTimestampAndActorTogether() {
        assertThatThrownBy(() -> new RoleAssignment(
            RoleAssignmentId.random(), actor, RoleId.random(), tenantId, ScopeType.SELF,
            null, GrantOrigin.DIRECT, null, Instant.EPOCH, Instant.EPOCH, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("revokedAt", "revokedBy");
        assertThatThrownBy(() -> new RoleAssignment(
            RoleAssignmentId.random(), actor, RoleId.random(), tenantId, ScopeType.SELF,
            null, GrantOrigin.DIRECT, null, Instant.EPOCH, null, actor))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("revokedAt", "revokedBy");
    }
}
