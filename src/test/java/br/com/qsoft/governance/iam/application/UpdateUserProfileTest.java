package br.com.qsoft.governance.iam.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UpdateUserProfileTest {

    private InMemoryUserRepository userRepository;
    private UpdateUserProfile updateUserProfile;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        updateUserProfile = new UpdateUserProfile(userRepository);
    }

    @Test
    void shouldUpdateUserProfileDepartmentAndAdminStatus() {
        TenantId tenantId = TenantId.random();
        User user = User.create(UserId.random(), tenantId, "test@test.com", "Test User", HierarchyLevel.COLLABORATOR, null);
        userRepository.save(user);

        UUID newDeptId = UUID.randomUUID();
        User updated = updateUserProfile.updateProfile(new UpdateUserProfileCommand(
            tenantId,
            user.id(),
            newDeptId,
            HierarchyLevel.MANAGER,
            true
        ));

        assertEquals(newDeptId, updated.departmentId());
        assertEquals(HierarchyLevel.MANAGER, updated.hierarchyLevel());
        assertTrue(updated.isAdmin());
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
