package br.com.qsoft.governance.iam.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IdentityQueryTest {

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

        @Override
        public List<User> findActiveByTenantId(TenantId tenantId) {
            return usersById.values().stream()
                .filter(u -> u.tenantId().equals(tenantId) && u.status() == UserStatus.ACTIVE)
                .sorted(Comparator.comparing(User::name).thenComparing(u -> u.id().value()))
                .toList();
        }
    };

    private final RoleAssignmentRepository roleAssignmentRepository =
        mock(RoleAssignmentRepository.class);

    private final IdentityQueryAdapter adapter =
        new IdentityQueryAdapter(userRepository, roleAssignmentRepository);

    @Test
    void shouldReturnUserContextWithHierarchyAndName() {
        TenantId tenantId = TenantId.random();
        User user = User.create(UserId.random(), tenantId, "ana@x.com", "Ana",
            HierarchyLevel.MANAGER, null);
        usersById.put(user.id(), user);

        UserContext context = adapter.getUserContext(user.id()).orElseThrow();

        assertThat(context.userId()).isEqualTo(user.id());
        assertThat(context.tenantId()).isEqualTo(tenantId);
        assertThat(context.hierarchyLevel()).isEqualTo("MANAGER");
        assertThat(context.status()).isEqualTo("ACTIVE");
        assertThat(context.name()).isEqualTo("Ana");
    }

    @Test
    void shouldReturnEmptyForUnknownUser() {
        assertThat(adapter.getUserContext(UserId.random())).isEmpty();
    }

    @Test
    void eligibleUsersExcludeOnlyTenantAdminAndCarryName() {
        TenantId tenantId = TenantId.random();
        User admin = User.create(UserId.random(), tenantId, "admin@x.com", "Admin",
            HierarchyLevel.EXECUTIVE, null);
        User member = User.create(UserId.random(), tenantId, "ana@x.com", "Ana",
            HierarchyLevel.COLLABORATOR, null);
        usersById.put(admin.id(), admin);
        usersById.put(member.id(), member);
        when(roleAssignmentRepository.findUserIdsWithOnlyTenantAdminGrant(tenantId))
            .thenReturn(Set.of(admin.id()));

        List<UserContext> eligible = adapter.eligibleUsers(tenantId);

        assertThat(eligible).extracting(UserContext::userId).containsExactly(member.id());
        assertThat(eligible.get(0).name()).isEqualTo("Ana");
    }
}
