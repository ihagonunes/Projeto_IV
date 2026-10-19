package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.sharedkernel.id.RoleAssignmentId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;

/**
 * Dados de entrada para revogar uma concessão (ADR-37 item 6): o alvo
 * ({@code targetUserId}) e a concessão ({@code assignmentId}).
 */
public record RevokeRoleAssignmentCommand(
    TenantId tenantId,
    UserId actor,
    UserId targetUserId,
    RoleAssignmentId assignmentId
) {
}
