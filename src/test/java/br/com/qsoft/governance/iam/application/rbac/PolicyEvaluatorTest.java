package br.com.qsoft.governance.iam.application.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.iam.domain.rbac.Permission;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.RoleAssignmentId;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PolicyEvaluatorTest {

    private final Map<TenantId, Tenant> tenants = new LinkedHashMap<>();
    private final Map<UserId, User> users = new LinkedHashMap<>();
    private final Map<RoleId, Role> roles = new LinkedHashMap<>();
    private final List<RoleAssignment> assignments = new ArrayList<>();

    private final TenantRepository tenantRepository = new TenantRepository() {
        @Override
        public Tenant save(Tenant tenant) {
            tenants.put(tenant.id(), tenant);
            return tenant;
        }

        @Override
        public Optional<Tenant> findById(TenantId id) {
            return Optional.ofNullable(tenants.get(id));
        }

        @Override
        public Optional<Tenant> findBySlug(String slug) {
            return tenants.values().stream().filter(t -> t.slug().equals(slug)).findFirst();
        }

        @Override
        public boolean existsBySlug(String slug) {
            return tenants.values().stream().anyMatch(t -> t.slug().equals(slug));
        }
    };

    private final UserRepository userRepository = new UserRepository() {
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
                .filter(u -> u.tenantId().equals(tenantId) && u.email().equals(email))
                .findFirst();
        }

        @Override
        public boolean existsByTenantIdAndEmail(TenantId tenantId, String email) {
            return findByTenantIdAndEmail(tenantId, email).isPresent();
        }
    };

    private final RoleRepository roleRepository = new RoleRepository() {
        @Override
        public Role save(Role role) {
            roles.put(role.id(), role);
            return role;
        }

        @Override
        public Optional<Role> findById(RoleId id) {
            return Optional.ofNullable(roles.get(id));
        }

        @Override
        public Optional<Role> findByTenantIdAndName(TenantId tenantId, String name) {
            return roles.values().stream()
                .filter(r -> r.tenantId().equals(tenantId) && r.name().equals(name))
                .findFirst();
        }

        @Override
        public boolean existsByTenantIdAndName(TenantId tenantId, String name) {
            return findByTenantIdAndName(tenantId, name).isPresent();
        }
    };

    private final RoleAssignmentRepository roleAssignmentRepository = new RoleAssignmentRepository() {
        @Override
        public RoleAssignment save(RoleAssignment assignment) {
            assignments.add(assignment);
            return assignment;
        }

        @Override
        public Optional<RoleAssignment> findById(RoleAssignmentId id) {
            return assignments.stream().filter(a -> a.id().equals(id)).findFirst();
        }

        @Override
        public List<RoleAssignment> findByTenantIdAndUserId(TenantId tenantId, UserId userId) {
            return assignments.stream()
                .filter(a -> a.tenantId().equals(tenantId) && a.userId().equals(userId))
                .toList();
        }

        @Override
        public void deleteById(RoleAssignmentId id) {
            assignments.removeIf(a -> a.id().equals(id));
        }
    };

    private final PolicyEvaluator evaluator = new PolicyEvaluator(
        userRepository, tenantRepository, roleRepository, roleAssignmentRepository);

    private final Permission manageUsers = Permission.of(Resource.USER, Action.MANAGE);

    private TenantId activeTenant() {
        TenantId id = TenantId.random();
        tenants.put(id, Tenant.restore(id, "t-" + id, "Tenant", TenantStatus.ACTIVE, Instant.now(), Instant.now()));
        return id;
    }

    private UserId activeUser(TenantId tenantId) {
        UserId id = UserId.random();
        users.put(id, User.create(id, tenantId, id + "@x.com", "User", HierarchyLevel.MANAGER, null));
        return id;
    }

    private RoleId roleWith(TenantId tenantId, Permission permission) {
        RoleId id = RoleId.random();
        Role role = Role.create(id, tenantId, "role-" + id);
        if (permission != null) {
            role.grantPermission(permission);
        }
        roles.put(id, role);
        return id;
    }

    private void assign(TenantId tenantId, UserId userId, RoleId roleId, UUID departmentId) {
        assignments.add(RoleAssignment.create(userId, roleId, tenantId, departmentId));
    }

    @Test
    void shouldAllowWhenRoleHasPermission() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);
        RoleId roleId = roleWith(tenantId, manageUsers);
        assign(tenantId, userId, roleId, null);

        assertThat(evaluator.isAllowed(EvaluationContext.of(tenantId, userId), manageUsers)).isTrue();
    }

    @Test
    void shouldDenyWhenRoleLacksPermission() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);
        RoleId roleId = roleWith(tenantId, Permission.of(Resource.USER, Action.READ));
        assign(tenantId, userId, roleId, null);

        assertThat(evaluator.isAllowed(EvaluationContext.of(tenantId, userId), manageUsers)).isFalse();
    }

    @Test
    void shouldDenyWhenNoAssignment() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);

        assertThat(evaluator.isAllowed(EvaluationContext.of(tenantId, userId), manageUsers)).isFalse();
    }

    @Test
    void shouldRespectDepartmentScope() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);
        RoleId roleId = roleWith(tenantId, manageUsers);
        UUID departmentA = UUID.randomUUID();
        UUID departmentB = UUID.randomUUID();
        assign(tenantId, userId, roleId, departmentA);

        assertThat(evaluator.isAllowed(new EvaluationContext(tenantId, userId, departmentA), manageUsers)).isTrue();
        assertThat(evaluator.isAllowed(new EvaluationContext(tenantId, userId, departmentB), manageUsers)).isFalse();
        assertThat(evaluator.isAllowed(EvaluationContext.of(tenantId, userId), manageUsers)).isFalse();
    }

    @Test
    void shouldDenyWhenUserInactive() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);
        RoleId roleId = roleWith(tenantId, manageUsers);
        assign(tenantId, userId, roleId, null);
        users.get(userId).deactivate();

        assertThat(evaluator.isAllowed(EvaluationContext.of(tenantId, userId), manageUsers)).isFalse();
    }

    @Test
    void shouldDenyWhenTenantInactive() {
        TenantId tenantId = TenantId.random();
        tenants.put(tenantId, Tenant.restore(tenantId, "inactive", "Inactive",
            TenantStatus.INACTIVE, Instant.now(), Instant.now()));
        UserId userId = activeUser(tenantId);
        RoleId roleId = roleWith(tenantId, manageUsers);
        assign(tenantId, userId, roleId, null);

        assertThat(evaluator.isAllowed(EvaluationContext.of(tenantId, userId), manageUsers)).isFalse();
    }

    @Test
    void shouldDenyWhenUserFromAnotherTenant() {
        TenantId tenantId = activeTenant();
        TenantId otherTenant = activeTenant();
        UserId userId = activeUser(otherTenant);
        RoleId roleId = roleWith(tenantId, manageUsers);
        assign(tenantId, userId, roleId, null);

        assertThat(evaluator.isAllowed(EvaluationContext.of(tenantId, userId), manageUsers)).isFalse();
    }

    @Test
    void shouldReturnEffectivePermissionsFromRoles() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);
        RoleId roleId = roleWith(tenantId, manageUsers);
        roles.get(roleId).grantPermission(Permission.of(Resource.TENANT, Action.READ));
        assign(tenantId, userId, roleId, null);

        assertThat(evaluator.effectivePermissions(EvaluationContext.of(tenantId, userId)))
            .containsExactlyInAnyOrder(
                Permission.of(Resource.USER, Action.MANAGE),
                Permission.of(Resource.TENANT, Action.READ));
    }

    @Test
    void shouldReturnEmptyEffectivePermissionsForInvalidContext() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);
        RoleId roleId = roleWith(tenantId, manageUsers);
        assign(tenantId, userId, roleId, null);
        users.get(userId).deactivate();

        assertThat(evaluator.effectivePermissions(EvaluationContext.of(tenantId, userId))).isEmpty();
    }

    @Test
    void shouldDenyWhenUserDoesNotExist() {
        TenantId tenantId = activeTenant();
        UserId nonExistentUser = UserId.random();

        var result = evaluator.evaluate(EvaluationContext.of(tenantId, nonExistentUser), manageUsers);
        assertThat(result.allowed()).isFalse();
        assertThat(result.denialReason()).isEqualTo("Usuário inexistente ou inativo.");
    }

    @Test
    void shouldDenyWhenTenantDoesNotExist() {
        TenantId nonExistentTenant = TenantId.random();
        UserId userId = UserId.random();
        users.put(userId, User.create(userId, nonExistentTenant, "test@x.com", "Test", HierarchyLevel.MANAGER, null));

        var result = evaluator.evaluate(EvaluationContext.of(nonExistentTenant, userId), manageUsers);
        assertThat(result.allowed()).isFalse();
        assertThat(result.denialReason()).isEqualTo("Tenant inexistente ou inativo.");
    }


    @Test
    void shouldIgnoreAssignmentWhenRoleDoesNotExist() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);
        RoleId nonExistentRole = RoleId.random();
        assign(tenantId, userId, nonExistentRole, null);

        assertThat(evaluator.isAllowed(EvaluationContext.of(tenantId, userId), manageUsers)).isFalse();
    }

    @Test
    void shouldThrowExceptionWhenContextOrPermissionIsNull() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);
        EvaluationContext context = EvaluationContext.of(tenantId, userId);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> evaluator.evaluate(null, manageUsers))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("context é obrigatório.");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> evaluator.evaluate(context, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("permission é obrigatória.");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> evaluator.effectivePermissions(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("context é obrigatório.");
    }
}

