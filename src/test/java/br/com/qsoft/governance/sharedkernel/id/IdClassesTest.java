package br.com.qsoft.governance.sharedkernel.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdClassesTest {

    @Test
    void testTenantId() {
        UUID uuid = UUID.randomUUID();
        TenantId tenantId = TenantId.of(uuid);

        assertThat(tenantId.value()).isEqualTo(uuid);
        assertThat(tenantId.toString()).isEqualTo(uuid.toString());

        TenantId random = TenantId.random();
        assertThat(random).isNotNull();

        TenantId fromStr = TenantId.fromString(uuid.toString());
        assertThat(fromStr).isEqualTo(tenantId);

        assertThatThrownBy(() -> new TenantId(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testUserId() {
        UUID uuid = UUID.randomUUID();
        UserId userId = UserId.of(uuid);

        assertThat(userId.value()).isEqualTo(uuid);
        assertThat(userId.toString()).isEqualTo(uuid.toString());

        UserId random = UserId.random();
        assertThat(random).isNotNull();

        UserId fromStr = UserId.fromString(uuid.toString());
        assertThat(fromStr).isEqualTo(userId);

        assertThatThrownBy(() -> new UserId(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testRoleId() {
        UUID uuid = UUID.randomUUID();
        RoleId roleId = RoleId.of(uuid);

        assertThat(roleId.value()).isEqualTo(uuid);
        assertThat(roleId.toString()).isEqualTo(uuid.toString());

        RoleId random = RoleId.random();
        assertThat(random).isNotNull();

        RoleId fromStr = RoleId.fromString(uuid.toString());
        assertThat(fromStr).isEqualTo(roleId);

        assertThatThrownBy(() -> new RoleId(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testInviteId() {
        UUID uuid = UUID.randomUUID();
        InviteId inviteId = InviteId.of(uuid);

        assertThat(inviteId.value()).isEqualTo(uuid);
        assertThat(inviteId.toString()).isEqualTo(uuid.toString());

        InviteId random = InviteId.random();
        assertThat(random).isNotNull();

        assertThatThrownBy(() -> new InviteId(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testRoleAssignmentId() {
        UUID uuid = UUID.randomUUID();
        RoleAssignmentId assignmentId = RoleAssignmentId.of(uuid);

        assertThat(assignmentId.value()).isEqualTo(uuid);
        assertThat(assignmentId.toString()).isEqualTo(uuid.toString());

        RoleAssignmentId random = RoleAssignmentId.random();
        assertThat(random).isNotNull();

        assertThatThrownBy(() -> new RoleAssignmentId(null))
            .isInstanceOf(NullPointerException.class);
    }
}
