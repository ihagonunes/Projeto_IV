package br.com.qsoft.governance.iam.domain.rbac;

import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Objects;
import java.util.UUID;

/**
 * Policy de concessão de acesso: atribui um {@link Role} a um usuário dentro de
 * um tenant, com escopo opcional de Department.
 *
 * O Department é referenciado apenas por {@code departmentId} (UUID), sem FK
 * SQL nem aggregate duplicado no IAM (ADR-05/ADR-29).
 */
public record RoleAssignment(UserId userId, RoleId roleId, TenantId tenantId, UUID departmentId) {

    public RoleAssignment {
        Objects.requireNonNull(userId, "userId é obrigatório.");
        Objects.requireNonNull(roleId, "roleId é obrigatório.");
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
    }

    public boolean isDepartmentScoped() {
        return departmentId != null;
    }
}
