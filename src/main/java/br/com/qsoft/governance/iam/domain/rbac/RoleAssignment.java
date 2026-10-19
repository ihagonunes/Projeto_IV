package br.com.qsoft.governance.iam.domain.rbac;

import br.com.qsoft.governance.sharedkernel.id.RoleAssignmentId;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.AccessTarget;
import br.com.qsoft.governance.sharedkernel.rbac.ScopeType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Concessão de acesso: atribui um {@link Role} de sistema a um usuário dentro
 * de um tenant, com escopo fixo derivado do papel (ADR-36/ADR-37).
 *
 * O escopo pertence à concessão e nunca é derivado do rótulo ou do departamento
 * atual do usuário. O Department é referenciado apenas por {@code departmentId}
 * (UUID), sem FK SQL nem aggregate duplicado no IAM (ADR-05/ADR-29).
 */
public record RoleAssignment(
        RoleAssignmentId id,
        UserId userId,
        RoleId roleId,
        TenantId tenantId,
        ScopeType scopeType,
        UUID departmentId,
        GrantOrigin origin,
        UserId grantedBy,
        Instant grantedAt,
        Instant revokedAt,
        UserId revokedBy) {

    public RoleAssignment {
        Objects.requireNonNull(id, "id é obrigatório.");
        Objects.requireNonNull(userId, "userId é obrigatório.");
        Objects.requireNonNull(roleId, "roleId é obrigatório.");
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
        Objects.requireNonNull(scopeType, "scopeType é obrigatório.");
        Objects.requireNonNull(origin, "origin é obrigatório.");
        Objects.requireNonNull(grantedAt, "grantedAt é obrigatório.");
        if ((scopeType == ScopeType.DEPARTMENT) != (departmentId != null)) {
            throw new IllegalArgumentException(
                "departmentId é obrigatório se e somente se o escopo for DEPARTMENT.");
        }
        if ((revokedAt == null) != (revokedBy == null)) {
            throw new IllegalArgumentException(
                "revokedAt e revokedBy devem ser ambos nulos ou ambos preenchidos.");
        }
    }

    /**
     * Cria uma concessão ativa usando o escopo fixo do papel. O departamento é
     * descartado quando o escopo do papel não é {@code DEPARTMENT}.
     */
    public static RoleAssignment grant(UserId userId, Role role, UUID departmentId,
                                       GrantOrigin origin, UserId grantedBy, Instant now) {
        Objects.requireNonNull(userId, "userId é obrigatório.");
        Objects.requireNonNull(role, "role é obrigatório.");
        Objects.requireNonNull(origin, "origin é obrigatório.");
        Objects.requireNonNull(now, "now é obrigatório.");
        SystemRole systemRole = role.systemRole()
            .orElseThrow(() -> new IllegalArgumentException(
                "Papel sem escopo de sistema: " + role.name() + "."));
        ScopeType scope = systemRole.scope();
        UUID scopedDepartment = scope == ScopeType.DEPARTMENT ? departmentId : null;
        if (scope == ScopeType.DEPARTMENT && scopedDepartment == null) {
            throw new IllegalArgumentException("Concessão DEPARTMENT exige departmentId.");
        }
        return new RoleAssignment(RoleAssignmentId.random(), userId, role.id(), role.tenantId(),
            scope, scopedDepartment, origin, grantedBy, now, null, null);
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    /** Mantido para o avaliador legado: indica que o escopo é DEPARTMENT. */
    public boolean isDepartmentScoped() {
        return scopeType == ScopeType.DEPARTMENT;
    }

    /** Revogação lógica e idempotente (ADR-37): reexecutar não altera nada. */
    public RoleAssignment revoke(UserId actor, Instant now) {
        Objects.requireNonNull(actor, "actor é obrigatório.");
        Objects.requireNonNull(now, "now é obrigatório.");
        if (!isActive()) {
            return this;
        }
        return new RoleAssignment(id, userId, roleId, tenantId, scopeType, departmentId, origin,
            grantedBy, grantedAt, now, actor);
    }

    /**
     * Cobertura da concessão sobre um alvo (ADR-36):
     * TENANT cobre qualquer alvo; DEPARTMENT(d) cobre {@code DepartmentTarget(d)} e
     * {@code OwnedTarget(_, d)}; SELF cobre {@code OwnedTarget(ator, _)}.
     */
    public boolean covers(AccessTarget target) {
        Objects.requireNonNull(target, "target é obrigatório.");
        return switch (scopeType) {
            case TENANT -> true;
            case DEPARTMENT -> (target instanceof AccessTarget.DepartmentTarget department
                    && Objects.equals(department.departmentId(), departmentId))
                || (target instanceof AccessTarget.OwnedTarget owned
                    && Objects.equals(owned.ownerDepartment(), departmentId));
            case SELF -> target instanceof AccessTarget.OwnedTarget owned
                && Objects.equals(owned.owner(), userId);
        };
    }
}
