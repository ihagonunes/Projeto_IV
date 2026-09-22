package br.com.qsoft.governance.iam.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChangeUserStatusTest {

    private InMemoryUserRepository userRepository;
    private ChangeUserStatus changeUserStatus;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        changeUserStatus = new ChangeUserStatus(userRepository);
    }

    @Test
    void shouldDeactivateUser() {
        TenantId tenantId = TenantId.random();
        UserId adminId = UserId.random();
        UserId targetId = UserId.random();

        User targetUser = User.create(targetId, tenantId, "target@test.com", "Target", HierarchyLevel.COLLABORATOR, null);
        userRepository.save(targetUser);

        User updated = changeUserStatus.changeStatus(new ChangeUserStatusCommand(
            tenantId,
            adminId,
            targetId,
            UserStatus.INACTIVE
        ));

        assertEquals(UserStatus.INACTIVE, updated.status());
    }

    @Test
    void shouldPreventSelfDeactivation() {
        TenantId tenantId = TenantId.random();
        UserId adminId = UserId.random();

        User adminUser = User.create(adminId, tenantId, "admin@test.com", "Admin", HierarchyLevel.EXECUTIVE, null);
        userRepository.save(adminUser);

        assertThrows(IllegalArgumentException.class, () -> {
            changeUserStatus.changeStatus(new ChangeUserStatusCommand(
                tenantId,
                adminId,
                adminId,
                UserStatus.INACTIVE
            ));
        });
    }

    static class InMemoryUserRepository implements UserRepository {
        private final java.util.Map<UserId, User> users = new java.util.HashMap<>();

        @Override
        public User save(User user) {
            users.put(user.id(), user);
            return user;
        }

        @Override
        public Optional<User> findById(UserId id) {
            return Optional.ofNullable(users.get(id));
        }

        @Override
        public Optional<User> findByTenantIdAndEmail(TenantId tenantId, String email) {
            return users.values().stream()
                .filter(u -> u.tenantId().equals(tenantId) && u.email().equalsIgnoreCase(email))
                .findFirst();
        }

        @Override
        public boolean existsByTenantIdAndEmail(TenantId tenantId, String email) {
            return findByTenantIdAndEmail(tenantId, email).isPresent();
        }

        @Override
        public List<User> findAllByTenantId(TenantId tenantId) {
            return users.values().stream()
                .filter(u -> u.tenantId().equals(tenantId))
                .toList();
        }
    }
}
