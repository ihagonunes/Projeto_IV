package br.com.qsoft.governance.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.iam.application.rbac.EvaluationContext;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IAMApplicationRecordsTest {

    @Test
    void testAcceptInviteCommand() {
        AcceptInviteCommand cmd = new AcceptInviteCommand("token123", "Ana Silva", "Secret@123");
        assertThat(cmd.token()).isEqualTo("token123");
        assertThat(cmd.name()).isEqualTo("Ana Silva");
        assertThat(cmd.password()).isEqualTo("Secret@123");
    }

    @Test
    void testAuthenticatedUser() {
        UserId userId = UserId.random();
        TenantId tenantId = TenantId.random();
        AuthenticatedUser user = new AuthenticatedUser(userId, tenantId, "ana@qsoft.com", "ADMIN");

        assertThat(user.userId()).isEqualTo(userId);
        assertThat(user.tenantId()).isEqualTo(tenantId);
        assertThat(user.email()).isEqualTo("ana@qsoft.com");
        assertThat(user.hierarchyLevel()).isEqualTo("ADMIN");
    }

    @Test
    void testChangeUserHierarchyLevelCommand() {
        UserId actorUserId = UserId.random();
        UserId targetUserId = UserId.random();
        ChangeUserHierarchyLevelCommand cmd = new ChangeUserHierarchyLevelCommand(
            actorUserId, targetUserId, HierarchyLevel.EXECUTIVE);

        assertThat(cmd.actorUserId()).isEqualTo(actorUserId);
        assertThat(cmd.targetUserId()).isEqualTo(targetUserId);
        assertThat(cmd.newLevel()).isEqualTo(HierarchyLevel.EXECUTIVE);
    }

    @Test
    void testChangeUserStatusCommand() {
        UserId actorUserId = UserId.random();
        UserId targetUserId = UserId.random();
        ChangeUserStatusCommand cmd = new ChangeUserStatusCommand(
            actorUserId, targetUserId, UserStatus.INACTIVE);

        assertThat(cmd.actorUserId()).isEqualTo(actorUserId);
        assertThat(cmd.targetUserId()).isEqualTo(targetUserId);
        assertThat(cmd.status()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void testCreateInviteCommand() {
        TenantId tenantId = TenantId.random();
        UUID deptId = UUID.randomUUID();
        CreateInviteCommand cmd = new CreateInviteCommand(
            tenantId, "ana@qsoft.com", HierarchyLevel.MANAGER, deptId, null);

        assertThat(cmd.tenantId()).isEqualTo(tenantId);
        assertThat(cmd.email()).isEqualTo("ana@qsoft.com");
        assertThat(cmd.hierarchyLevel()).isEqualTo(HierarchyLevel.MANAGER);
        assertThat(cmd.departmentId()).isEqualTo(deptId);
    }

    @Test
    void testCreateUserCommand() {
        TenantId tenantId = TenantId.random();
        UUID deptId = UUID.randomUUID();
        CreateUserCommand cmd = new CreateUserCommand(
            tenantId, "ana@qsoft.com", "Ana", HierarchyLevel.MANAGER, deptId);

        assertThat(cmd.tenantId()).isEqualTo(tenantId);
        assertThat(cmd.email()).isEqualTo("ana@qsoft.com");
        assertThat(cmd.name()).isEqualTo("Ana");
        assertThat(cmd.hierarchyLevel()).isEqualTo(HierarchyLevel.MANAGER);
        assertThat(cmd.departmentId()).isEqualTo(deptId);
    }

    @Test
    void testListUsersCommand() {
        TenantId tenantId = TenantId.random();
        UUID deptId = UUID.randomUUID();
        ListUsersCommand cmd = new ListUsersCommand(tenantId, HierarchyLevel.MANAGER, deptId, "Ana", "ana@qsoft.com", 0, 10);

        assertThat(cmd.tenantId()).isEqualTo(tenantId);
        assertThat(cmd.hierarchyLevel()).isEqualTo(HierarchyLevel.MANAGER);
        assertThat(cmd.departmentId()).isEqualTo(deptId);
        assertThat(cmd.name()).isEqualTo("Ana");
        assertThat(cmd.email()).isEqualTo("ana@qsoft.com");
        assertThat(cmd.page()).isZero();
        assertThat(cmd.size()).isEqualTo(10);
    }

    @Test
    void testLoginCommand() {
        LoginCommand cmd = new LoginCommand("qsoft", "ana@qsoft.com", "pass");
        assertThat(cmd.tenantSlug()).isEqualTo("qsoft");
        assertThat(cmd.email()).isEqualTo("ana@qsoft.com");
        assertThat(cmd.password()).isEqualTo("pass");
    }

    @Test
    void testProvisionUserCommand() {
        TenantId tenantId = TenantId.random();
        UUID deptId = UUID.randomUUID();
        ProvisionUserCommand cmd = new ProvisionUserCommand(
            tenantId, "ana@qsoft.com", "Ana", HierarchyLevel.MANAGER, "Pass123!", deptId, Set.of());

        assertThat(cmd.tenantId()).isEqualTo(tenantId);
        assertThat(cmd.email()).isEqualTo("ana@qsoft.com");
        assertThat(cmd.name()).isEqualTo("Ana");
        assertThat(cmd.hierarchyLevel()).isEqualTo(HierarchyLevel.MANAGER);
        assertThat(cmd.password()).isEqualTo("Pass123!");
        assertThat(cmd.departmentId()).isEqualTo(deptId);
        assertThat(cmd.permissions()).isEmpty();
    }

    @Test
    void testRegisterTenantCommand() {
        RegisterTenantCommand cmd = new RegisterTenantCommand("qsoft", "QSOFT Inc", "qsoft.com");
        assertThat(cmd.slug()).isEqualTo("qsoft");
        assertThat(cmd.name()).isEqualTo("QSOFT Inc");
        assertThat(cmd.emailDomain()).isEqualTo("qsoft.com");
    }

    @Test
    void testRegisterUserCommand() {
        UserId actorUserId = UserId.random();
        TenantId tenantId = TenantId.random();
        UUID deptId = UUID.randomUUID();

        RegisterUserCommand cmd = new RegisterUserCommand(
            actorUserId, tenantId, "admin@qsoft.com", "Admin", HierarchyLevel.MANAGER, deptId);

        assertThat(cmd.actorUserId()).isEqualTo(actorUserId);
        assertThat(cmd.tenantId()).isEqualTo(tenantId);
        assertThat(cmd.email()).isEqualTo("admin@qsoft.com");
        assertThat(cmd.name()).isEqualTo("Admin");
        assertThat(cmd.hierarchyLevel()).isEqualTo(HierarchyLevel.MANAGER);
        assertThat(cmd.departmentId()).isEqualTo(deptId);
    }

    @Test
    void testResendInviteCommand() {
        TenantId tenantId = TenantId.random();
        InviteId inviteId = InviteId.random();
        ResendInviteCommand cmd = new ResendInviteCommand(tenantId, inviteId);

        assertThat(cmd.tenantId()).isEqualTo(tenantId);
        assertThat(cmd.inviteId()).isEqualTo(inviteId);
    }

    @Test
    void testRevokeInviteCommand() {
        TenantId tenantId = TenantId.random();
        InviteId inviteId = InviteId.random();
        RevokeInviteCommand cmd = new RevokeInviteCommand(tenantId, inviteId);

        assertThat(cmd.tenantId()).isEqualTo(tenantId);
        assertThat(cmd.inviteId()).isEqualTo(inviteId);
    }

    @Test
    void testUpdateTenantBrandingCommand() {
        TenantId tenantId = TenantId.random();
        UpdateTenantBrandingCommand cmd = new UpdateTenantBrandingCommand(
            tenantId, "https://logo.png", "#FFFFFF", "#000000");

        assertThat(cmd.tenantId()).isEqualTo(tenantId);
        assertThat(cmd.logoUrl()).isEqualTo("https://logo.png");
        assertThat(cmd.primaryColor()).isEqualTo("#FFFFFF");
        assertThat(cmd.secondaryColor()).isEqualTo("#000000");
    }

    @Test
    void testUpdateTenantCommand() {
        TenantId tenantId = TenantId.random();
        UpdateTenantCommand cmd = new UpdateTenantCommand(tenantId, "New Name");

        assertThat(cmd.tenantId()).isEqualTo(tenantId);
        assertThat(cmd.name()).isEqualTo("New Name");
    }

    @Test
    void testUpdateUserCommand() {
        UserId actorUserId = UserId.random();
        UserId targetUserId = UserId.random();
        UUID deptId = UUID.randomUUID();
        UpdateUserCommand cmd = new UpdateUserCommand(actorUserId, targetUserId, deptId);

        assertThat(cmd.actorUserId()).isEqualTo(actorUserId);
        assertThat(cmd.targetUserId()).isEqualTo(targetUserId);
        assertThat(cmd.departmentId()).isEqualTo(deptId);
    }

    @Test
    void testEvaluationContext() {
        TenantId tenantId = TenantId.random();
        UserId userId = UserId.random();
        UUID deptId = UUID.randomUUID();

        EvaluationContext ctx = new EvaluationContext(tenantId, userId, deptId);
        assertThat(ctx.tenantId()).isEqualTo(tenantId);
        assertThat(ctx.userId()).isEqualTo(userId);
        assertThat(ctx.departmentId()).isEqualTo(deptId);

        EvaluationContext ofCtx = EvaluationContext.of(tenantId, userId);
        assertThat(ofCtx.departmentId()).isNull();

        assertThatThrownBy(() -> new EvaluationContext(null, userId, deptId))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new EvaluationContext(tenantId, null, deptId))
            .isInstanceOf(NullPointerException.class);
    }
}
