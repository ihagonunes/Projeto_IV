package br.com.qsoft.governance.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.application.rbac.DominancePolicy;
import br.com.qsoft.governance.iam.application.rbac.TenantAdminGuard;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.LastTenantAdminException;
import br.com.qsoft.governance.iam.domain.exception.UserNotFoundException;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChangeUserStatusTest {

    private final Map<UserId, User> usersById = new LinkedHashMap<>();
    private final DominancePolicy dominancePolicy = mock(DominancePolicy.class);
    private final TenantAdminGuard tenantAdminGuard = mock(TenantAdminGuard.class);

    private final UserRepository userRepository = new UserRepository() {
        @Override
        public User save(User user) {
            usersById.put(user.id(), user);
            return user;
        }

        @Override
        public Optional<User> findById(UserId id) {
            return Optional.ofNullable(usersById.get(id));
        }

        @Override
        public Optional<User> findByTenantIdAndEmail(TenantId tenantId, String email) {
            return usersById.values().stream()
                .filter(u -> u.tenantId().equals(tenantId) && u.email().equals(email))
                .findFirst();
        }

        @Override
        public boolean existsByTenantIdAndEmail(TenantId tenantId, String email) {
            return usersById.values().stream()
                .anyMatch(u -> u.tenantId().equals(tenantId) && u.email().equals(email));
        }
    };

    private final ChangeUserStatus useCase =
        new ChangeUserStatus(userRepository, dominancePolicy, tenantAdminGuard);

    @BeforeEach
    void allowDominance() {
        when(dominancePolicy.dominates(any(), any(), any())).thenReturn(true);
    }

    private User userIn(TenantId tenantId, String email, HierarchyLevel level) {
        User user = User.create(UserId.random(), tenantId, email, email, level, null);
        usersById.put(user.id(), user);
        return user;
    }

    @Test
    void shouldDeactivateUserPreservingHistory() {
        TenantId tenantId = TenantId.random();
        User actor = userIn(tenantId, "admin@x.com", HierarchyLevel.EXECUTIVE);
        User target = userIn(tenantId, "ana@x.com", HierarchyLevel.COLLABORATOR);

        User updated = useCase.change(new ChangeUserStatusCommand(
            actor.id(), target.id(), UserStatus.INACTIVE));

        assertThat(updated.status()).isEqualTo(UserStatus.INACTIVE);
        User persisted = userRepository.findById(target.id()).orElseThrow();
        assertThat(persisted.email()).isEqualTo("ana@x.com");
        assertThat(persisted.hierarchyLevel()).isEqualTo(HierarchyLevel.COLLABORATOR);
        assertThat(persisted.status()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void shouldReactivateUserRestoringAccess() {
        TenantId tenantId = TenantId.random();
        User actor = userIn(tenantId, "admin@x.com", HierarchyLevel.EXECUTIVE);
        User target = userIn(tenantId, "ana@x.com", HierarchyLevel.COLLABORATOR);
        target.deactivate();

        User updated = useCase.change(new ChangeUserStatusCommand(
            actor.id(), target.id(), UserStatus.ACTIVE));

        assertThat(updated.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(userRepository.findById(target.id()).orElseThrow().status())
            .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void shouldRejectChangeAcrossTenants() {
        User actor = userIn(TenantId.random(), "admin@a.com", HierarchyLevel.EXECUTIVE);
        User target = userIn(TenantId.random(), "ana@b.com", HierarchyLevel.COLLABORATOR);

        assertThatThrownBy(() -> useCase.change(new ChangeUserStatusCommand(
            actor.id(), target.id(), UserStatus.INACTIVE)))
            .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void shouldRejectUnknownTarget() {
        TenantId tenantId = TenantId.random();
        User actor = userIn(tenantId, "admin@x.com", HierarchyLevel.EXECUTIVE);

        assertThatThrownBy(() -> useCase.change(new ChangeUserStatusCommand(
            actor.id(), UserId.random(), UserStatus.INACTIVE)))
            .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void shouldRejectUnknownActor() {
        TenantId tenantId = TenantId.random();
        User target = userIn(tenantId, "ana@x.com", HierarchyLevel.COLLABORATOR);

        assertThatThrownBy(() -> useCase.change(new ChangeUserStatusCommand(
            UserId.random(), target.id(), UserStatus.INACTIVE)))
            .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void shouldRejectSelfDeactivationWithForbidden() {
        TenantId tenantId = TenantId.random();
        User actor = userIn(tenantId, "admin@x.com", HierarchyLevel.EXECUTIVE);

        assertThatThrownBy(() -> useCase.change(new ChangeUserStatusCommand(
            actor.id(), actor.id(), UserStatus.INACTIVE)))
            .isInstanceOf(AuthorizationDeniedException.class);

        assertThat(userRepository.findById(actor.id()).orElseThrow().status())
            .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void shouldRejectDeactivatingLastTenantAdminWithConflict() {
        TenantId tenantId = TenantId.random();
        User actor = userIn(tenantId, "admin@x.com", HierarchyLevel.EXECUTIVE);
        doThrow(new LastTenantAdminException())
            .when(tenantAdminGuard).ensureCanRemoveTenantAdmin(tenantId, actor.id());

        assertThatThrownBy(() -> useCase.change(new ChangeUserStatusCommand(
            actor.id(), actor.id(), UserStatus.INACTIVE)))
            .isInstanceOf(LastTenantAdminException.class);

        assertThat(userRepository.findById(actor.id()).orElseThrow().status())
            .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void shouldRejectNullStatus() {
        TenantId tenantId = TenantId.random();
        User actor = userIn(tenantId, "admin@x.com", HierarchyLevel.EXECUTIVE);
        User target = userIn(tenantId, "ana@x.com", HierarchyLevel.COLLABORATOR);

        assertThatThrownBy(() -> useCase.change(new ChangeUserStatusCommand(
            actor.id(), target.id(), null)))
            .isInstanceOf(NullPointerException.class);
    }
}
