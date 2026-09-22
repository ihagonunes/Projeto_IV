package br.com.qsoft.governance.iam.application;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResendUserInviteTest {

    private InMemoryUserRepository userRepository;
    private ResendUserInvite resendUserInvite;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        resendUserInvite = new ResendUserInvite(userRepository);
    }

    @Test
    void shouldResendInviteForValidUser() {
        TenantId tenantId = TenantId.random();
        UserId userId = UserId.random();

        User user = User.create(userId, tenantId, "invitee@test.com", "Invitee", HierarchyLevel.COLLABORATOR, null);
        userRepository.save(user);

        User result = resendUserInvite.resend(new ResendUserInviteCommand(tenantId, userId));

        assertNotNull(result);
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
