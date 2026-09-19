package br.com.qsoft.governance.iam.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListUsersTest {

    private InMemoryUserRepository userRepository;
    private ListUsers listUsers;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        listUsers = new ListUsers(userRepository);
    }

    @Test
    void shouldListUsersForGivenTenant() {
        TenantId tenant1 = TenantId.random();
        TenantId tenant2 = TenantId.random();

        User user1 = User.create(UserId.random(), tenant1, "u1@t1.com", "User 1", HierarchyLevel.COLLABORATOR, null);
        User user2 = User.create(UserId.random(), tenant1, "u2@t1.com", "User 2", HierarchyLevel.MANAGER, null);
        User user3 = User.create(UserId.random(), tenant2, "u3@t2.com", "User 3", HierarchyLevel.EXECUTIVE, null);

        userRepository.save(user1);
        userRepository.save(user2);
        userRepository.save(user3);

        List<User> result = listUsers.list(new ListUsersQuery(tenant1, null, null));

        assertEquals(2, result.size());
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
