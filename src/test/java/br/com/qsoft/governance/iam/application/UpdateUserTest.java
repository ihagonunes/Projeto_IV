package br.com.qsoft.governance.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.application.rbac.DominancePolicy;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.UserNotFoundException;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Edição do Department com as guardas da SPEC-01 §4 (ator ≠ alvo, domina,
 * departamento no escopo).
 */
class UpdateUserTest {

    private final Map<UserId, User> usersById = new LinkedHashMap<>();
    private final DominancePolicy dominancePolicy = mock(DominancePolicy.class);

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

    private final UpdateUser useCase = new UpdateUser(userRepository, dominancePolicy);

    @BeforeEach
    void allowDominance() {
        when(dominancePolicy.dominates(any(), any(), any())).thenReturn(true);
        when(dominancePolicy.coversDepartment(any(), any(), any())).thenReturn(true);
    }

    private User userIn(TenantId tenantId, String email, HierarchyLevel level, UUID departmentId) {
        User user = User.create(UserId.random(), tenantId, email, email, level, departmentId);
        usersById.put(user.id(), user);
        return user;
    }

    @Test
    void shouldUpdateDepartmentWithinSameTenant() {
        TenantId tenantId = TenantId.random();
        User actor = userIn(tenantId, "admin@x.com", HierarchyLevel.EXECUTIVE, null);
        User target = userIn(tenantId, "ana@x.com", HierarchyLevel.COLLABORATOR, null);
        UUID marketing = UUID.randomUUID();

        User updated = useCase.update(new UpdateUserCommand(actor.id(), target.id(), marketing));

        assertThat(updated.departmentId()).isEqualTo(marketing);
        assertThat(userRepository.findById(target.id()).orElseThrow().departmentId())
            .isEqualTo(marketing);
        assertThat(updated.hierarchyLevel()).isEqualTo(HierarchyLevel.COLLABORATOR);
    }

    @Test
    void shouldRejectChangeAcrossTenants() {
        User actor = userIn(TenantId.random(), "admin@a.com", HierarchyLevel.EXECUTIVE, null);
        User target = userIn(TenantId.random(), "ana@b.com", HierarchyLevel.COLLABORATOR, null);

        assertThatThrownBy(() -> useCase.update(
            new UpdateUserCommand(actor.id(), target.id(), UUID.randomUUID())))
            .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void shouldRejectSelfChange() {
        TenantId tenantId = TenantId.random();
        User actor = userIn(tenantId, "admin@x.com", HierarchyLevel.EXECUTIVE, null);

        assertThatThrownBy(() -> useCase.update(
            new UpdateUserCommand(actor.id(), actor.id(), UUID.randomUUID())))
            .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void shouldRejectWhenActorDoesNotDominateTarget() {
        TenantId tenantId = TenantId.random();
        User actor = userIn(tenantId, "manager@x.com", HierarchyLevel.MANAGER, null);
        User target = userIn(tenantId, "admin@x.com", HierarchyLevel.EXECUTIVE, null);
        when(dominancePolicy.dominates(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.update(
            new UpdateUserCommand(actor.id(), target.id(), UUID.randomUUID())))
            .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void shouldRejectDepartmentOutsideActorScope() {
        TenantId tenantId = TenantId.random();
        User actor = userIn(tenantId, "manager@x.com", HierarchyLevel.MANAGER, UUID.randomUUID());
        User target = userIn(tenantId, "ana@x.com", HierarchyLevel.COLLABORATOR, null);
        when(dominancePolicy.coversDepartment(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.update(
            new UpdateUserCommand(actor.id(), target.id(), UUID.randomUUID())))
            .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void shouldRejectUnknownTarget() {
        TenantId tenantId = TenantId.random();
        User actor = userIn(tenantId, "admin@x.com", HierarchyLevel.EXECUTIVE, null);

        assertThatThrownBy(() -> useCase.update(
            new UpdateUserCommand(actor.id(), UserId.random(), UUID.randomUUID())))
            .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void shouldRejectMissingDepartment() {
        TenantId tenantId = TenantId.random();
        User actor = userIn(tenantId, "admin@x.com", HierarchyLevel.EXECUTIVE, null);
        User target = userIn(tenantId, "ana@x.com", HierarchyLevel.COLLABORATOR, null);

        assertThatThrownBy(() -> useCase.update(new UpdateUserCommand(actor.id(), target.id(), null)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
