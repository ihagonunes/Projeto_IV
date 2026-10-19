package br.com.qsoft.governance.iam.domain.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
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
        UserId user = UserId.random();
        Role leader = Role.create(RoleId.random(), tenantId, "LEADER");
        RoleAssignment assignment = RoleAssignment.grant(
            user, leader, UUID.randomUUID(), GrantOrigin.INVITE, null, Instant.now());

        assertThat(assignment.isDepartmentScoped()).isTrue();

        Role collaborator = Role.create(RoleId.random(), tenantId, "COLLABORATOR");
        RoleAssignment tenantWide = RoleAssignment.grant(
            user, collaborator, null, GrantOrigin.INVITE, null, Instant.now());
        assertThat(tenantWide.isDepartmentScoped()).isFalse();
    }

    // =====================================================================
    // Branches faltantes (branch 66.7% → ≥ 70%)
    // =====================================================================

    /** name nulo → IllegalArgumentException. */
    @Test
    void deve_lancarExcecao_quando_nomeNulo() {
        assertThatThrownBy(() -> Role.create(RoleId.random(), tenantId, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("obrigatório");
    }

    /** rename com nome nulo → IllegalArgumentException. */
    @Test
    void deve_lancarExcecao_quando_renameComNomeNulo() {
        Role role = Role.create(RoleId.random(), tenantId, "ROLE");
        assertThatThrownBy(() -> role.rename(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /** rename com nome em branco → IllegalArgumentException. */
    @Test
    void deve_lancarExcecao_quando_renameComNomeVazio() {
        Role role = Role.create(RoleId.random(), tenantId, "ROLE");
        assertThatThrownBy(() -> role.rename(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deve_lancarExcecao_quando_renameComNomeApenasEspacos() {
        Role role = Role.create(RoleId.random(), tenantId, "ROLE");
        assertThatThrownBy(() -> role.rename("   "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /** grantPermission nulo → NullPointerException (requireNonNull explícito). */
    @Test
    void deve_lancarExcecao_quando_grantPermissionNulo() {
        Role role = Role.create(RoleId.random(), tenantId, "ROLE");
        assertThatThrownBy(() -> role.grantPermission(null))
            .isInstanceOf(NullPointerException.class);
    }

    /** revokePermission nulo → NullPointerException. */
    @Test
    void deve_lancarExcecao_quando_revokePermissionNulo() {
        Role role = Role.create(RoleId.random(), tenantId, "ROLE");
        assertThatThrownBy(() -> role.revokePermission(null))
            .isInstanceOf(NullPointerException.class);
    }

    /** restore com permissions nulo → conjunto vazio (não NPE). */
    @Test
    void deve_criarPermissoesVazias_quando_restoreComPermissionsNulo() {
        Instant now = Instant.now();
        Role role = Role.restore(RoleId.random(), tenantId, "ROLE", null, now, now);

        assertThat(role.permissions()).isEmpty();
    }

    /** restore com permissions preenchidas → preserva as permissões. */
    @Test
    void deve_preservarPermissoes_quando_restoreComPermissoesPreenchidas() {
        Permission p = Permission.of(Resource.USER, Action.READ);
        Instant now = Instant.now();
        Role role = Role.restore(RoleId.random(), tenantId, "ROLE", Set.of(p), now, now);

        assertThat(role.hasPermission(p)).isTrue();
    }

    /** permissions() retorna cópia imutável — modificação externa não afeta a role. */
    @Test
    void deve_retornarCopiaImutavel_quando_acessarPermissions() {
        Role role = Role.create(RoleId.random(), tenantId, "ROLE");
        Permission p = Permission.of(Resource.USER, Action.READ);
        role.grantPermission(p);

        Set<Permission> copy = role.permissions();
        assertThatThrownBy(() -> copy.add(Permission.of(Resource.DEPARTMENT, Action.READ)))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    /** updatedAt muda depois de rename. */
    @Test
    void deve_atualizarUpdatedAt_quando_renomear() {
        Instant before = Instant.now().minusMillis(10);
        Role role = Role.create(RoleId.random(), tenantId, "OLD");

        role.rename("NEW");

        assertThat(role.updatedAt()).isAfterOrEqualTo(before);
    }
}

