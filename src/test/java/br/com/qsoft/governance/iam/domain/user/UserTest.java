package br.com.qsoft.governance.iam.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Testes unitários do agregado {@link User}.
 * Domínio puro — sem Spring, sem banco, sem mocks.
 */
class UserTest {

    private final TenantId tenantId = TenantId.random();

    private User activeUser() {
        return User.create(UserId.random(), tenantId, "ana@qsoft.com", "Ana",
            HierarchyLevel.COLLABORATOR, null);
    }

    @Test
    void deve_manterStatusAtivo_quando_activateJaEstaAtivo() {
        User user = activeUser();
        Instant before = user.updatedAt();

        user.activate();

        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
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

        user.deactivate();

        assertThat(user.status()).isEqualTo(UserStatus.INACTIVE);
        assertThat(user.updatedAt()).isEqualTo(before);
    }

    @Test
    void deve_retornarFalse_quando_passwordHashEhNulo() {
        User user = User.restore(
            UserId.random(), tenantId, "x@x.com", "X",
            HierarchyLevel.COLLABORATOR, null, null,
            UserStatus.ACTIVE, Instant.now(), Instant.now());

        assertThat(user.hasPasswordHash()).isFalse();
    }

    @Test
    void deve_retornarFalse_quando_passwordHashEhBranco() {
        User user = User.restore(
            UserId.random(), tenantId, "x@x.com", "X",
            HierarchyLevel.COLLABORATOR, null, "   ",
            UserStatus.ACTIVE, Instant.now(), Instant.now());

        assertThat(user.hasPasswordHash()).isFalse();
    }

    @Test
    void deve_retornarTrue_quando_passwordHashPreenchido() {
        User user = activeUser();
        user.setPasswordHash("$2a$10$hash");

        assertThat(user.hasPasswordHash()).isTrue();
    }

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

    @Test
    void deve_removerDepartamento_quando_changeDepartmentRecebeNull() {
        UUID dept = UUID.randomUUID();
        User user = User.create(UserId.random(), tenantId, "x@x.com", "X",
            HierarchyLevel.COLLABORATOR, dept);

        user.changeDepartment(null);

        assertThat(user.departmentId()).isNull();
    }
}
