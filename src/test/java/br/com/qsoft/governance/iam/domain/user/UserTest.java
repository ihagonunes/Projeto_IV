package br.com.qsoft.governance.iam.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.iam.api.model.Permission;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Testes unitários do agregado {@link User}.
 * Domínio puro — sem Spring, sem banco, sem mocks.
 * Cobre os branches não testados na cobertura anterior (branch 50%).
 */
class UserTest {

    private final TenantId tenantId = TenantId.random();

    /** Fábrica helper: usuário ativo com defaults razoáveis. */
    private User activeUser() {
        return User.create(UserId.random(), tenantId, "ana@qsoft.com", "Ana",
            HierarchyLevel.COLLABORATOR, null);
    }

    // -------------------------------------------------------------------------
    // activate / deactivate — idempotência
    // -------------------------------------------------------------------------

    @Test
    void deve_manterStatusAtivo_quando_activateJaEstaAtivo() {
        User user = activeUser();
        Instant before = user.updatedAt();

        user.activate(); // idempotente: já é ACTIVE

        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        // updatedAt não é alterado quando já está no estado correto
        assertThat(user.updatedAt()).isEqualTo(before);
    }

    @Test
    void deve_desativar_quando_usuarioEstaAtivo() {
        User user = activeUser();

        user.deactivate();

        assertThat(user.status()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void deve_reativar_quando_usuarioEstaInativo() {
        User user = activeUser();
        user.deactivate();

        user.activate();

        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void deve_manterStatusInativo_quando_deactivateJaEstaInativo() {
        User user = activeUser();
        user.deactivate();
        Instant before = user.updatedAt();

        user.deactivate(); // idempotente: já é INACTIVE

        assertThat(user.status()).isEqualTo(UserStatus.INACTIVE);
        assertThat(user.updatedAt()).isEqualTo(before);
    }

    // -------------------------------------------------------------------------
    // grantPermission / revokePermission — guard clause com null
    // -------------------------------------------------------------------------

    @Test
    void deve_ignorar_quando_grantPermissionRecebeNull() {
        User user = activeUser();

        // null é guard clause: retorna sem modificar nem lançar exceção
        user.grantPermission(null);

        assertThat(user.permissions()).isEmpty();
        assertThat(user.hasPermission(null)).isFalse();
    }

    @Test
    void deve_ignorar_quando_revokePermissionRecebeNull() {
        User user = activeUser();
        Permission p = Permission.MANAGE_USERS;
        user.grantPermission(p);

        // null é guard clause: retorna sem lançar exceção nem remover p
        user.revokePermission(null);

        assertThat(user.permissions()).containsExactly(p);
    }

    @Test
    void deve_concederERetirarPermissao_quando_permissaoValida() {
        User user = activeUser();
        Permission p = Permission.MANAGE_USERS;

        user.grantPermission(p);
        assertThat(user.hasPermission(p)).isTrue();

        user.revokePermission(p);
        assertThat(user.hasPermission(p)).isFalse();
    }

    // -------------------------------------------------------------------------
    // restore com permissions null → deve tratar como conjunto vazio
    // -------------------------------------------------------------------------

    @Test
    void deve_criarComPermissoesVazias_quando_permissionsNuloNoRestore() {
        User user = User.restore(
            UserId.random(), tenantId, "x@x.com", "X",
            HierarchyLevel.MANAGER, null, "hash",
            null, UserStatus.ACTIVE, Instant.now(), Instant.now());

        assertThat(user.permissions()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // hasPasswordHash — branch: null vs blank vs preenchido
    // -------------------------------------------------------------------------

    @Test
    void deve_retornarFalse_quando_passwordHashEhNulo() {
        User user = User.restore(
            UserId.random(), tenantId, "x@x.com", "X",
            HierarchyLevel.COLLABORATOR, null, null,
            Set.of(), UserStatus.ACTIVE, Instant.now(), Instant.now());

        assertThat(user.hasPasswordHash()).isFalse();
    }

    @Test
    void deve_retornarFalse_quando_passwordHashEhBranco() {
        User user = User.restore(
            UserId.random(), tenantId, "x@x.com", "X",
            HierarchyLevel.COLLABORATOR, null, "   ",
            Set.of(), UserStatus.ACTIVE, Instant.now(), Instant.now());

        assertThat(user.hasPasswordHash()).isFalse();
    }

    @Test
    void deve_retornarTrue_quando_passwordHashPreenchido() {
        User user = activeUser();
        user.setPasswordHash("$2a$10$hash");

        assertThat(user.hasPasswordHash()).isTrue();
    }

    // -------------------------------------------------------------------------
    // isAdmin — conjunto completo de permissões
    // -------------------------------------------------------------------------

    @Test
    void deve_retornarFalse_quando_naoTemTodasPermissoes() {
        User user = activeUser();
        user.grantPermission(Permission.MANAGE_USERS);

        assertThat(user.isAdmin()).isFalse();
    }

    @Test
    void deve_retornarTrue_quando_temTodasPermissoes() {
        User user = activeUser();
        for (Permission p : Permission.values()) {
            user.grantPermission(p);
        }

        assertThat(user.isAdmin()).isTrue();
    }

    // -------------------------------------------------------------------------
    // changeHierarchyLevel — null lança exceção
    // -------------------------------------------------------------------------

    @Test
    void deve_lancarExcecao_quando_changeHierarchyLevelRecebeNull() {
        User user = activeUser();

        assertThatThrownBy(() -> user.changeHierarchyLevel(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("hierarchyLevel");
    }

    @Test
    void deve_alterarNivel_quando_nivelValido() {
        User user = activeUser();

        user.changeHierarchyLevel(HierarchyLevel.MANAGER);

        assertThat(user.hierarchyLevel()).isEqualTo(HierarchyLevel.MANAGER);
    }

    // -------------------------------------------------------------------------
    // changeDepartment — aceita null (remove associação)
    // -------------------------------------------------------------------------

    @Test
    void deve_removerDepartamento_quando_changeDepartmentRecebeNull() {
        UUID dept = UUID.randomUUID();
        User user = User.create(UserId.random(), tenantId, "x@x.com", "X",
            HierarchyLevel.COLLABORATOR, dept);

        user.changeDepartment(null);

        assertThat(user.departmentId()).isNull();
    }

    // -------------------------------------------------------------------------
    // permissions() retorna cópia imutável
    // -------------------------------------------------------------------------

    @Test
    void deve_retornarCopiaImutavel_quando_acessarPermissions() {
        User user = activeUser();
        user.grantPermission(Permission.MANAGE_USERS);

        Set<Permission> permissions = user.permissions();

        assertThatThrownBy(() -> permissions.add(Permission.MANAGE_DEPARTMENTS))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
