package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import java.util.UUID;

/**
 * Representação pública de uma concessão criada.
 */
public record AssignmentResponse(
    UUID id,
    String scope,
    UUID departmentId,
    String origin
) {

    public static AssignmentResponse from(RoleAssignment assignment) {
        return new AssignmentResponse(
            assignment.id().value(),
            assignment.scopeType().name(),
            assignment.departmentId(),
            assignment.origin().name()
        );
    }
}
