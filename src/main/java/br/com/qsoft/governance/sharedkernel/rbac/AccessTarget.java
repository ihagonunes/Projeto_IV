package br.com.qsoft.governance.sharedkernel.rbac;

import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Objects;
import java.util.UUID;

/**
 * Alvo de uma decisão fina de autorização (ADR-36). O módulo dono do recurso
 * resolve o alvo; o IAM avalia a cobertura da concessão sobre ele.
 */
public sealed interface AccessTarget {

    /** Qualquer recurso do tenant. */
    record TenantTarget() implements AccessTarget {
    }

    /** Um departamento específico ({@code departmentId != null}). */
    record DepartmentTarget(UUID departmentId) implements AccessTarget {

        public DepartmentTarget {
            Objects.requireNonNull(departmentId, "departmentId é obrigatório.");
        }
    }

    /** Recurso pertencente a um usuário ({@code owner != null}; depto pode ser nulo). */
    record OwnedTarget(UserId owner, UUID ownerDepartment) implements AccessTarget {

        public OwnedTarget {
            Objects.requireNonNull(owner, "owner é obrigatório.");
        }
    }
}
