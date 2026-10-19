package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.domain.rbac.GrantOrigin;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.UUID;

/**
 * Dados de entrada para atribuir uma concessão a um usuário existente (ADR-37 6a).
 * {@code actor} é quem atribui; {@code target} é o usuário alvo.
 */
public record AssignRoleCommand(
    TenantId tenantId,
    UserId actor,
    UserId target,
    String roleKey,
    UUID departmentId,
    GrantOrigin origin
) {
}
