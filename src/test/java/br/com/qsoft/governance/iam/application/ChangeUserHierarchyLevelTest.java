package br.com.qsoft.governance.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.iam.api.model.AuthorizationResult;
import br.com.qsoft.governance.iam.api.model.AuthorizationScope;
import br.com.qsoft.governance.iam.api.model.Permission;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
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

    private final AuthorizationQuery authorizationQuery = new AuthorizationQuery() {
        @Override
        public AuthorizationResult checkPermission(UserId userId, Permission permission) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return AuthorizationResult.deny("Usuário não encontrado.");
            }
            if (user.status() != UserStatus.ACTIVE) {
                return AuthorizationResult.deny("Usuário inativo.");
            }
            if (!user.hasPermission(permission)) {
                return AuthorizationResult.deny("Permissão negada: " + permission.name() + ".");
            }
            return AuthorizationResult.allow();
        }

        @Override
        public AuthorizationScope resolveScope(UserId userId) {
            return AuthorizationScope.SELF;
        }
    };

    private final ChangeUserHierarchyLevel useCase =
        new ChangeUserHierarchyLevel(userRepository, authorizationQuery);

    private User userIn(TenantId tenantId, String email, HierarchyLevel level, Permission... permissions) {
        User user = User.create(UserId.random(), tenantId, email, email, level, null);
        for (Permission permission : permissions) {
            user.grantPermission(permission);
        }
        usersById.put(user.id(), user);
        return user;
    }

    @Test
    void shouldChangeHierarchyLevelWithinSameTenant() {
        TenantId tenantId = TenantId.random();
        User actor = userIn(tenantId, "admin@x.com", HierarchyLevel.EXECUTIVE, Permission.MANAGE_HIERARCHY);
        User target = userIn(tenantId, "ana@x.com", HierarchyLevel.COLLABORATOR);

        User updated = useCase.change(new ChangeUserHierarchyLevelCommand(
            actor.id(), target.id(), HierarchyLevel.MANAGER));

        assertThat(updated.hierarchyLevel()).isEqualTo(HierarchyLevel.MANAGER);
        assertThat(userRepository.findById(target.id()).orElseThrow().hierarchyLevel())
            .isEqualTo(HierarchyLevel.MANAGER);
        assertThat(updated.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void shouldRejectWhenActorLacksPermission() {
        TenantId tenantId = TenantId.random();
        User actor = userIn(tenantId, "user@x.com", HierarchyLevel.COLLABORATOR);
        User target = userIn(tenantId, "ana@x.com", HierarchyLevel.COLLABORATOR);

        assertThatThrownBy(() -> useCase.change(new ChangeUserHierarchyLevelCommand(
            actor.id(), target.id(), HierarchyLevel.MANAGER)))
            .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void shouldRejectChangeAcrossTenants() {
        User actor = userIn(TenantId.random(), "admin@a.com", HierarchyLevel.EXECUTIVE,
            Permission.MANAGE_HIERARCHY);
        User target = userIn(TenantId.random(), "ana@b.com", HierarchyLevel.COLLABORATOR);

        assertThatThrownBy(() -> useCase.change(new ChangeUserHierarchyLevelCommand(
            actor.id(), target.id(), HierarchyLevel.MANAGER)))
            .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void shouldRejectUnknownTarget() {
        TenantId tenantId = TenantId.random();
        User actor = userIn(tenantId, "admin@x.com", HierarchyLevel.EXECUTIVE,
            Permission.MANAGE_HIERARCHY);

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
