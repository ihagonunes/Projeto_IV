package br.com.qsoft.governance.iam.application.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.iam.domain.rbac.Permission;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.iam.domain.rbac.GrantOrigin;
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
import br.com.qsoft.governance.sharedkernel.rbac.AccessTarget;
import br.com.qsoft.governance.sharedkernel.rbac.ScopeType;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
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
        public List<RoleAssignment> findActiveByTenantIdAndUserId(TenantId tenantId, UserId userId) {
            return assignments.stream()
                .filter(a -> a.isActive()
                    && a.tenantId().equals(tenantId) && a.userId().equals(userId))
                .toList();
        }

        @Override
        public long countActiveByTenantIdAndRoleId(TenantId tenantId, RoleId roleId) {
            return assignments.stream()
                .filter(a -> a.isActive()
                    && a.tenantId().equals(tenantId) && a.roleId().equals(roleId))
                .count();
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

    private Role roleWith(TenantId tenantId, Permission permission) {
        return roleWith(tenantId, permission, "EXECUTIVE");
    }

    private Role roleWith(TenantId tenantId, Permission permission, String name) {
        RoleId id = RoleId.random();
        Role role = Role.create(id, tenantId, name);
        if (permission != null) {
            role.grantPermission(permission);
        }
        roles.put(id, role);
        return role;
    }

    private void assign(TenantId tenantId, UserId userId, Role role, UUID departmentId) {
        assignments.add(RoleAssignment.grant(
            userId, role, departmentId, br.com.qsoft.governance.iam.domain.rbac.GrantOrigin.MIGRATION,
            null, Instant.now()));
    }

    @Test
    void byTargetAllowsDepartmentGrantButTenantGateRequiresTenantScopeAndHonorsChatCeiling() {
        TenantId tenant = activeTenant();
        UserId actor = activeUser(tenant);
        assign(tenant, actor, roleWith(tenant, manageUsers, "MANAGER"), UUID.randomUUID());
        assign(tenant, actor, roleWith(tenant, Permission.of(Resource.CHAT, Action.READ), "EXECUTIVE"), null);

        assertThat(evaluator.gateByTarget(tenant, actor, Resource.USER, Action.MANAGE)).isTrue();
        assertThat(evaluator.gateTenantOnly(tenant, actor, Resource.USER, Action.MANAGE)).isFalse();
        assertThat(evaluator.gateTenantOnly(tenant, actor, Resource.CHAT, Action.READ)).isFalse();
        assertThat(evaluator.isAllowed(tenant, actor, Resource.CHAT, Action.READ, new AccessTarget.TenantTarget()))
            .isFalse();
        assertThat(evaluator.visibilityFor(tenant, actor, Resource.CHAT, Action.READ))
            .isEqualTo(new VisibilityScope.SelfOnly(actor));

        assign(tenant, actor, roleWith(tenant, manageUsers, "EXECUTIVE"), null);
        assertThat(evaluator.gateTenantOnly(tenant, actor, Resource.USER, Action.MANAGE)).isTrue();
    }

    @Test
    void scopedAuthorizationRejectsInactiveOrCrossTenantContext() {
        TenantId tenant = activeTenant();
        UserId actor = activeUser(tenant);
        assertThat(evaluator.gateByTarget(activeTenant(), actor, Resource.USER, Action.READ)).isFalse();
        tenants.remove(tenant);
        assertThat(evaluator.gateByTarget(tenant, actor, Resource.USER, Action.READ)).isFalse();
        tenants.put(tenant, Tenant.restore(tenant, "inactive", "Tenant", TenantStatus.INACTIVE,
            Instant.now(), Instant.now()));
        assertThat(evaluator.gateByTarget(tenant, actor, Resource.USER, Action.READ)).isFalse();
        users.get(actor).deactivate();
        assertThat(evaluator.gateByTarget(tenant, actor, Resource.USER, Action.READ)).isFalse();
    }

    @Test
    void scopedPermissionsIgnoreMissingForeignAndNonMatchingRoles() {
        TenantId tenant = activeTenant();
        UserId actor = activeUser(tenant);
        Role missing = roleWith(tenant, manageUsers);
        assign(tenant, actor, missing, null);
        roles.remove(missing.id());
        Role foreign = roleWith(activeTenant(), manageUsers);
        assignments.add(new RoleAssignment(RoleAssignmentId.random(), actor, foreign.id(),
            tenant, ScopeType.TENANT, null, GrantOrigin.MIGRATION, null, Instant.now(), null, null));
        Permission readUsage = Permission.of(Resource.USAGE, Action.READ);
        assign(tenant, actor, roleWith(tenant, readUsage), null);

        assertThat(evaluator.gateByTarget(tenant, actor, Resource.USER, Action.MANAGE)).isFalse();
        assertThat(evaluator.effectivePermissions(tenant, actor)).containsExactly(readUsage);
    }

    @Test
    void shouldAllowWhenRoleHasPermission() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);
        Role role = roleWith(tenantId, manageUsers);
        assign(tenantId, userId, role, null);

        assertThat(evaluator.isAllowed(EvaluationContext.of(tenantId, userId), manageUsers)).isTrue();
    }

    @Test
    void shouldDenyWhenRoleLacksPermission() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);
        Role role = roleWith(tenantId, Permission.of(Resource.USER, Action.READ));
        assign(tenantId, userId, role, null);

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
        Role role = roleWith(tenantId, manageUsers, "MANAGER");
        UUID departmentA = UUID.randomUUID();
        UUID departmentB = UUID.randomUUID();
        assign(tenantId, userId, role, departmentA);

        assertThat(evaluator.isAllowed(new EvaluationContext(tenantId, userId, departmentA), manageUsers)).isTrue();
        assertThat(evaluator.isAllowed(new EvaluationContext(tenantId, userId, departmentB), manageUsers)).isFalse();
        assertThat(evaluator.isAllowed(EvaluationContext.of(tenantId, userId), manageUsers)).isFalse();
    }

    @Test
    void shouldDenyWhenUserInactive() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);
        Role role = roleWith(tenantId, manageUsers);
        assign(tenantId, userId, role, null);
        users.get(userId).deactivate();

        assertThat(evaluator.isAllowed(EvaluationContext.of(tenantId, userId), manageUsers)).isFalse();
    }

    @Test
    void shouldDenyWhenTenantInactive() {
        TenantId tenantId = TenantId.random();
        tenants.put(tenantId, Tenant.restore(tenantId, "inactive", "Inactive",
            TenantStatus.INACTIVE, Instant.now(), Instant.now()));
        UserId userId = activeUser(tenantId);
        Role role = roleWith(tenantId, manageUsers);
        assign(tenantId, userId, role, null);

        assertThat(evaluator.isAllowed(EvaluationContext.of(tenantId, userId), manageUsers)).isFalse();
    }

    @Test
    void shouldDenyWhenUserFromAnotherTenant() {
        TenantId tenantId = activeTenant();
        TenantId otherTenant = activeTenant();
        UserId userId = activeUser(otherTenant);
        Role role = roleWith(tenantId, manageUsers);
        assign(tenantId, userId, role, null);

        assertThat(evaluator.isAllowed(EvaluationContext.of(tenantId, userId), manageUsers)).isFalse();
    }

    @Test
    void shouldReturnEffectivePermissionsFromRoles() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);
        Role role = roleWith(tenantId, manageUsers);
        role.grantPermission(Permission.of(Resource.TENANT, Action.READ));
        assign(tenantId, userId, role, null);

        assertThat(evaluator.effectivePermissions(EvaluationContext.of(tenantId, userId)))
            .containsExactlyInAnyOrder(
                Permission.of(Resource.USER, Action.MANAGE),
                Permission.of(Resource.TENANT, Action.READ));
    }

    @Test
    void shouldReturnEmptyEffectivePermissionsForInvalidContext() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);
        Role role = roleWith(tenantId, manageUsers);
        assign(tenantId, userId, role, null);
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
        Role nonExistentRole = Role.create(RoleId.random(), tenantId, "EXECUTIVE");
        assign(tenantId, userId, nonExistentRole, null);

        assertThat(evaluator.isAllowed(EvaluationContext.of(tenantId, userId), manageUsers)).isFalse();
    }

    // =====================================================================
    // ADR-36 §3 — avaliação com alvo, gate por escopo e visibilidade
    // =====================================================================

    /** AZ10: concessão SELF com MANAGE não cobre alvo departamental. */
    @Test
    void az10SelfGrantWithManageDoesNotCoverDepartmentTarget() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);
        Role role = roleWith(tenantId, manageUsers, "COLLABORATOR");
        assign(tenantId, userId, role, null);

        assertThat(evaluator.isAllowed(tenantId, userId, Resource.USER, Action.MANAGE,
            new AccessTarget.DepartmentTarget(UUID.randomUUID()))).isFalse();
        assertThat(evaluator.isAllowed(tenantId, userId, Resource.USER, Action.MANAGE,
            new AccessTarget.OwnedTarget(userId, null))).isTrue();
    }

    /** AZ12: EXECUTIVE (TENANT) não lê o CHAT de outro — teto CHAT = SELF. */
    @Test
    void az12ExecutiveCannotReadAnotherUsersChat() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);
        Role role = roleWith(tenantId, Permission.of(Resource.CHAT, Action.READ), "EXECUTIVE");
        assign(tenantId, userId, role, null);

        assertThat(evaluator.isAllowed(tenantId, userId, Resource.CHAT, Action.READ,
            new AccessTarget.OwnedTarget(UserId.random(), null))).isFalse();
        assertThat(evaluator.isAllowed(tenantId, userId, Resource.CHAT, Action.READ,
            new AccessTarget.OwnedTarget(userId, null))).isTrue();
    }

    /** AZ12: TENANT_ADMIN não tem CHAT (nem no gate, nem na decisão por alvo). */
    @Test
    void az12TenantAdminHasNoChatPermission() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);
        Role role = roleWith(tenantId, null, "TENANT_ADMIN");
        assign(tenantId, userId, role, null);

        assertThat(evaluator.gateByTarget(tenantId, userId, Resource.CHAT, Action.READ)).isFalse();
        assertThat(evaluator.isAllowed(tenantId, userId, Resource.CHAT, Action.READ,
            new AccessTarget.TenantTarget())).isFalse();
    }

    @Test
    void visibilityIsNoneWithoutApplicableGrant() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);

        assertThat(evaluator.visibilityFor(tenantId, userId, Resource.USER, Action.READ))
            .isEqualTo(new VisibilityScope.None());
    }

    @Test
    void visibilityIsSelfOnlyForSelfGrant() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);
        Role role = roleWith(tenantId, Permission.of(Resource.USER, Action.READ), "COLLABORATOR");
        assign(tenantId, userId, role, null);

        assertThat(evaluator.visibilityFor(tenantId, userId, Resource.USER, Action.READ))
            .isEqualTo(new VisibilityScope.SelfOnly(userId));
    }

    @Test
    void visibilityIsDepartmentsForDepartmentalGrants() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);
        UUID departmentA = UUID.randomUUID();
        UUID departmentB = UUID.randomUUID();
        Role roleA = roleWith(tenantId, Permission.of(Resource.USER, Action.READ), "MANAGER");
        assign(tenantId, userId, roleA, departmentA);
        Role roleB = roleWith(tenantId, Permission.of(Resource.USER, Action.READ), "MANAGER");
        assign(tenantId, userId, roleB, departmentB);

        VisibilityScope scope = evaluator.visibilityFor(tenantId, userId, Resource.USER, Action.READ);
        assertThat(scope).isInstanceOf(VisibilityScope.Departments.class);
        assertThat(((VisibilityScope.Departments) scope).ids())
            .containsExactlyInAnyOrder(departmentA, departmentB);
    }

    @Test
    void visibilityIsTenantWideWhenAnyGrantIsTenantScoped() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);
        Role departmentRole = roleWith(tenantId, Permission.of(Resource.USER, Action.READ), "MANAGER");
        assign(tenantId, userId, departmentRole, UUID.randomUUID());
        Role tenantRole = roleWith(tenantId, Permission.of(Resource.USER, Action.READ), "EXECUTIVE");
        assign(tenantId, userId, tenantRole, null);

        assertThat(evaluator.visibilityFor(tenantId, userId, Resource.USER, Action.READ))
            .isEqualTo(new VisibilityScope.TenantWide());
    }

    @Test
    void revokedGrantIsIgnored() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);
        Role role = roleWith(tenantId, Permission.of(Resource.USER, Action.READ), "EXECUTIVE");
        RoleAssignment grant = RoleAssignment.grant(
            userId, role, null, GrantOrigin.MIGRATION, null, Instant.now());
        assignments.add(grant.revoke(userId, Instant.now()));

        assertThat(evaluator.gateByTarget(tenantId, userId, Resource.USER, Action.READ)).isFalse();
        assertThat(evaluator.visibilityFor(tenantId, userId, Resource.USER, Action.READ))
            .isEqualTo(new VisibilityScope.None());
    }

    /**
     * SPEC §7.3: as permissões efetivas são a união das concessões ATIVAS, sem
     * filtro de escopo — em particular, concessões DEPARTMENT contam (regressão
     * encontrada na revisão A1–A6: TODO departamento era descartado).
     */
    @Test
    void effectivePermissionsByTenantIncludesDepartmentalGrants() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);
        Role role = roleWith(tenantId, Permission.of(Resource.USER, Action.READ), "MANAGER");
        assign(tenantId, userId, role, UUID.randomUUID());

        assertThat(evaluator.effectivePermissions(tenantId, userId))
            .contains(Permission.of(Resource.USER, Action.READ));
    }

    @Test
    void effectivePermissionsByTenantIsEmptyForInvalidContext() {
        TenantId tenantId = activeTenant();

        assertThat(evaluator.effectivePermissions(tenantId, UserId.random())).isEmpty();
    }

    @Test
    void roleFromAnotherTenantIsIgnored() {
        TenantId tenantId = activeTenant();
        TenantId otherTenant = activeTenant();
        UserId userId = activeUser(tenantId);
        Role foreignRole = Role.create(RoleId.random(), otherTenant, "EXECUTIVE");
        foreignRole.grantPermission(Permission.of(Resource.USER, Action.READ));
        roles.put(foreignRole.id(), foreignRole);
        // Estado inconsistente: concessão do tenant do ator com papel de outro tenant.
        assignments.add(new RoleAssignment(RoleAssignmentId.random(), userId, foreignRole.id(),
            tenantId, ScopeType.SELF, null, GrantOrigin.MIGRATION, null, Instant.now(), null, null));

        assertThat(evaluator.gateByTarget(tenantId, userId, Resource.USER, Action.READ)).isFalse();
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

    @Test
    void spendingAlertIsCompanyWideWithoutExpandingOtherManagerPermissions() {
        TenantId tenantId = activeTenant();
        UserId userId = activeUser(tenantId);
        Role role = roleWith(tenantId, Permission.of(Resource.SPENDING_ALERT, Action.MANAGE), "MANAGER");
        assign(tenantId, userId, role, UUID.randomUUID());
        assertThat(evaluator.isAllowed(tenantId, userId, Resource.SPENDING_ALERT, Action.MANAGE,
            new AccessTarget.TenantTarget())).isTrue();
        assertThat(evaluator.isAllowed(tenantId, userId, Resource.SPENDING_BUDGET, Action.MANAGE,
            new AccessTarget.TenantTarget())).isFalse();
        assertThat(evaluator.isAllowed(TenantId.random(), userId, Resource.SPENDING_ALERT, Action.MANAGE,
            new AccessTarget.TenantTarget())).isFalse();
        assignments.clear();
        assertThat(evaluator.isAllowed(tenantId, userId, Resource.SPENDING_ALERT, Action.MANAGE,
            new AccessTarget.TenantTarget())).isFalse();
    }
}

