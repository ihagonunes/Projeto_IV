package br.com.qsoft.governance.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.UserNotFoundException;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChangeUserHierarchyLevelTest {

    private final Map<UserId, User> usersById = new LinkedHashMap<>();

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

    private final ChangeUserHierarchyLevel useCase = new ChangeUserHierarchyLevel(userRepository);

    private User userIn(TenantId tenantId, String email, HierarchyLevel level) {
        User user = User.create(UserId.random(), tenantId, email, email, level, null);
        usersById.put(user.id(), user);
        return user;
    }

    @Test
    void shouldChangeHierarchyLevelWithinSameTenant() {
        TenantId tenantId = TenantId.random();
        User actor = userIn(tenantId, "admin@x.com", HierarchyLevel.EXECUTIVE);
        User target = userIn(tenantId, "ana@x.com", HierarchyLevel.COLLABORATOR);

        User updated = useCase.change(new ChangeUserHierarchyLevelCommand(
            actor.id(), target.id(), HierarchyLevel.MANAGER));

        assertThat(updated.hierarchyLevel()).isEqualTo(HierarchyLevel.MANAGER);
        assertThat(userRepository.findById(target.id()).orElseThrow().hierarchyLevel())
            .isEqualTo(HierarchyLevel.MANAGER);
        assertThat(updated.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void shouldRejectChangeAcrossTenants() {
        User actor = userIn(TenantId.random(), "admin@a.com", HierarchyLevel.EXECUTIVE);
        User target = userIn(TenantId.random(), "ana@b.com", HierarchyLevel.COLLABORATOR);

        assertThatThrownBy(() -> useCase.change(new ChangeUserHierarchyLevelCommand(
            actor.id(), target.id(), HierarchyLevel.MANAGER)))
            .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void shouldRejectUnknownTarget() {
        TenantId tenantId = TenantId.random();
        User actor = userIn(tenantId, "admin@x.com", HierarchyLevel.EXECUTIVE);

        assertThatThrownBy(() -> useCase.change(new ChangeUserHierarchyLevelCommand(
            actor.id(), UserId.random(), HierarchyLevel.MANAGER)))
            .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void shouldRejectUnknownActor() {
        TenantId tenantId = TenantId.random();
        User target = userIn(tenantId, "ana@x.com", HierarchyLevel.COLLABORATOR);

        assertThatThrownBy(() -> useCase.change(new ChangeUserHierarchyLevelCommand(
            UserId.random(), target.id(), HierarchyLevel.MANAGER)))
            .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void shouldRejectNullLevel() {
        TenantId tenantId = TenantId.random();
        User actor = userIn(tenantId, "admin@x.com", HierarchyLevel.EXECUTIVE);
        User target = userIn(tenantId, "ana@x.com", HierarchyLevel.COLLABORATOR);

        assertThatThrownBy(() -> useCase.change(new ChangeUserHierarchyLevelCommand(
            actor.id(), target.id(), null)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
