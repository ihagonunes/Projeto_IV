package br.com.qsoft.governance.iam.application.rbac;

import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.iam.domain.rbac.SystemRole;
import br.com.qsoft.governance.sharedkernel.rbac.ScopeType;
import java.util.Objects;
import java.util.UUID;

/**
 * Regras de cobertura de escopo de uma concessão (SPEC-01 §4).
 */
final class ScopeCoverage {

    private ScopeCoverage() {
    }

    /**
     * {@code escopoContém(c, R, d)}: a concessão do ator cobre o papel alvo no
     * departamento informado?
     */
    static boolean contains(RoleAssignment grant, SystemRole target, UUID departmentId) {
        if (grant.scopeType() == ScopeType.TENANT) {
            return true;
        }
        return grant.scopeType() == ScopeType.DEPARTMENT
            && target.scope() != ScopeType.TENANT
            && Objects.equals(departmentId, grant.departmentId());
    }

    /** A concessão do ator cobre o departamento informado? */
    static boolean coversDepartment(RoleAssignment grant, UUID departmentId) {
        return switch (grant.scopeType()) {
            case TENANT -> true;
            case DEPARTMENT -> Objects.equals(departmentId, grant.departmentId());
            case SELF -> false;
        };
    }
}
