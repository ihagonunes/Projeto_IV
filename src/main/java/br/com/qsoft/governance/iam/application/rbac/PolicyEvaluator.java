package br.com.qsoft.governance.iam.application.rbac;

import br.com.qsoft.governance.iam.api.model.AuthorizationResult;
import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.rbac.Permission;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.AccessTarget;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.ScopeType;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Policy evaluator: motor centralizado de avaliação de autorização (ADR-36).
 * <p>
 * Pré-condição comum: usuário ACTIVE, mesmo tenant e tenant ACTIVE. Caso
 * contrário, nega. As concessões aplicáveis são as concessões ATIVAS do ator no
 * tenant, cujo papel pertença ao mesmo tenant e contenha a permissão (match
 * exato). São lidas do banco a cada chamada, sem cache.
 */
@Service
public class PolicyEvaluator {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;

    public PolicyEvaluator(UserRepository userRepository,
                           TenantRepository tenantRepository,
                           RoleRepository roleRepository,
                           RoleAssignmentRepository roleAssignmentRepository) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.roleRepository = roleRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    // =====================================================================
    // Avaliação com alvo (ADR-36 §3)
    // =====================================================================

    /**
     * Decisão fina: alguma concessão aplicável cobre o alvo (com o teto de
     * recurso aplicado).
     */
    public boolean isAllowed(TenantId tenantId, UserId actor, Resource resource, Action action,
                             AccessTarget target) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
        Objects.requireNonNull(actor, "actor é obrigatório.");
        Objects.requireNonNull(resource, "resource é obrigatório.");
        Objects.requireNonNull(action, "action é obrigatório.");
        Objects.requireNonNull(target, "target é obrigatório.");
        return applicableGrants(tenantId, actor, Permission.of(resource, action)).stream()
            .anyMatch(grant -> coversWithCeiling(grant, resource, target));
    }

    /**
     * Gate {@code TENANT_ONLY}: exige alguma concessão aplicável cujo escopo
     * efetivo seja TENANT.
     */
    public boolean gateTenantOnly(TenantId tenantId, UserId actor, Resource resource, Action action) {
        return applicableGrants(tenantId, actor, Permission.of(resource, action)).stream()
            .anyMatch(grant -> effectiveScope(grant, resource) == ScopeType.TENANT);
    }

    /**
     * Gate {@code BY_TARGET}: exige qualquer concessão ativa aplicável; a
     * checagem fina do alvo é responsabilidade do caso de uso.
     */
    public boolean gateByTarget(TenantId tenantId, UserId actor, Resource resource, Action action) {
        return !applicableGrants(tenantId, actor, Permission.of(resource, action)).isEmpty();
    }

    /**
     * União das permissões das concessões ATIVAS do usuário no tenant, sem filtro
     * de escopo/departamento (SPEC-01 §7.3 — usado por {@code /me/permissoes}).
     * Não é decisão de autorização: o front usa apenas para exibir menus.
     */
    public Set<Permission> effectivePermissions(TenantId tenantId, UserId actor) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
        Objects.requireNonNull(actor, "actor é obrigatório.");
        if (!contextValid(tenantId, actor)) {
            return Set.of();
        }
        Set<Permission> permissions = new HashSet<>();
        for (RoleAssignment grant :
            roleAssignmentRepository.findActiveByTenantIdAndUserId(tenantId, actor)) {
            Role role = roleRepository.findById(grant.roleId()).orElse(null);
            if (role == null || !role.tenantId().equals(tenantId)) {
                continue;
            }
            permissions.addAll(role.permissions());
        }
        return Set.copyOf(permissions);
    }

    /** Escopo de visibilidade para consultas agregadas (ADR-36 §3). */
    public VisibilityScope visibilityFor(TenantId tenantId, UserId actor, Resource resource, Action action) {
        List<RoleAssignment> grants = applicableGrants(tenantId, actor, Permission.of(resource, action));
        if (grants.isEmpty()) {
            return new VisibilityScope.None();
        }
        if (grants.stream().anyMatch(grant -> effectiveScope(grant, resource) == ScopeType.TENANT)) {
            return new VisibilityScope.TenantWide();
        }
        Set<UUID> departments = grants.stream()
            .filter(grant -> effectiveScope(grant, resource) == ScopeType.DEPARTMENT)
            .map(RoleAssignment::departmentId)
            .filter(Objects::nonNull)
            .collect(Collectors.toUnmodifiableSet());
        if (!departments.isEmpty()) {
            return new VisibilityScope.Departments(departments, actor);
        }
        return new VisibilityScope.SelfOnly(actor);
    }

    // =====================================================================
    // Trilha legada (mantida até a remoção em S3-A8)
    // =====================================================================

    /**
     * @deprecated substituído por {@link #isAllowed(TenantId, UserId, Resource, Action, AccessTarget)}.
     */
    @Deprecated(forRemoval = true)
    public AuthorizationResult evaluate(EvaluationContext context, Permission permission) {
        Objects.requireNonNull(context, "context é obrigatório.");
        Objects.requireNonNull(permission, "permission é obrigatória.");

        AuthorizationResult invalid = validate(context);
        if (invalid != null) {
            return invalid;
        }

        if (applicableRoles(context).stream().anyMatch(role -> role.hasPermission(permission))) {
            return AuthorizationResult.allow();
        }
        return AuthorizationResult.deny(
            "Permissão negada: " + permission.resource() + ":" + permission.action() + ".");
    }

    /** @deprecated substituído pela decisão por alvo (ADR-36). */
    @Deprecated(forRemoval = true)
    public boolean isAllowed(EvaluationContext context, Permission permission) {
        return evaluate(context, permission).allowed();
    }

    /**
     * Conjunto de permissões efetivas do usuário no contexto (união das
     * permissões dos papéis aplicáveis). Vazio se o contexto for inválido.
     */
    public Set<Permission> effectivePermissions(EvaluationContext context) {
        Objects.requireNonNull(context, "context é obrigatório.");
        return applicableRoles(context).stream()
            .flatMap(role -> role.permissions().stream())
            .collect(Collectors.toUnmodifiableSet());
    }

    /** Valida o contexto; retorna a negação correspondente ou {@code null} se válido. */
    private AuthorizationResult validate(EvaluationContext context) {
        User user = userRepository.findById(context.userId()).orElse(null);
        if (user == null || user.status() != UserStatus.ACTIVE) {
            return AuthorizationResult.deny("Usuário inexistente ou inativo.");
        }
        if (!user.tenantId().equals(context.tenantId())) {
            return AuthorizationResult.deny("Usuário não pertence ao tenant do contexto.");
        }
        var tenant = tenantRepository.findById(context.tenantId()).orElse(null);
        if (tenant == null || tenant.status() != TenantStatus.ACTIVE) {
            return AuthorizationResult.deny("Tenant inexistente ou inativo.");
        }
        return null;
    }

    private List<Role> applicableRoles(EvaluationContext context) {
        if (validate(context) != null) {
            return List.of();
        }
        List<Role> roles = new ArrayList<>();
        for (RoleAssignment assignment :
            roleAssignmentRepository.findActiveByTenantIdAndUserId(context.tenantId(), context.userId())) {
            if (assignment.isDepartmentScoped()
                && !Objects.equals(assignment.departmentId(), context.departmentId())) {
                continue;
            }
            roleRepository.findById(assignment.roleId())
                .filter(role -> role.tenantId().equals(context.tenantId()))
                .ifPresent(roles::add);
        }
        return roles;
    }

    // =====================================================================
    // Helpers do modelo com escopo
    // =====================================================================

    /**
     * Concessões aplicáveis: ativas, do ator no tenant, cujo papel pertence ao
     * mesmo tenant (defesa contra papel de outro tenant) e contém a permissão.
     */
    private List<RoleAssignment> applicableGrants(TenantId tenantId, UserId actor, Permission permission) {
        if (!contextValid(tenantId, actor)) {
            return List.of();
        }
        List<RoleAssignment> grants = new ArrayList<>();
        for (RoleAssignment grant :
            roleAssignmentRepository.findActiveByTenantIdAndUserId(tenantId, actor)) {
            Role role = roleRepository.findById(grant.roleId()).orElse(null);
            if (role == null) {
                continue;
            }
            if (!role.tenantId().equals(tenantId)) {
                continue;
            }
            if (!role.hasPermission(permission)) {
                continue;
            }
            grants.add(grant);
        }
        return grants;
    }

    private boolean contextValid(TenantId tenantId, UserId actor) {
        User user = userRepository.findById(actor).orElse(null);
        if (user == null || user.status() != UserStatus.ACTIVE) {
            return false;
        }
        if (!user.tenantId().equals(tenantId)) {
            return false;
        }
        var tenant = tenantRepository.findById(tenantId).orElse(null);
        return tenant != null && tenant.status() == TenantStatus.ACTIVE;
    }

    /** Teto por recurso: CHAT tem escopo efetivo SELF, qualquer que seja a concessão. */
    private ScopeType effectiveScope(RoleAssignment grant, Resource resource) {
        if (resource == Resource.SPENDING_ALERT) return ScopeType.TENANT;
        return resource == Resource.CHAT ? ScopeType.SELF : grant.scopeType();
    }

    private boolean coversWithCeiling(RoleAssignment grant, Resource resource, AccessTarget target) {
        if (resource == Resource.SPENDING_ALERT) return target instanceof AccessTarget.TenantTarget;
        if (resource == Resource.CHAT) {
            return target instanceof AccessTarget.OwnedTarget owned
                && Objects.equals(owned.owner(), grant.userId());
        }
        return grant.covers(target);
    }
}
