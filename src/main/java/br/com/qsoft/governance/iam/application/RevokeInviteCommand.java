package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;

/**
 * Dados de entrada da revogação de convite. {@code actor} é quem revoga
 * (precisa poder conceder o papel do convite).
 */
public record RevokeInviteCommand(
    TenantId tenantId,
    InviteId inviteId,
    UserId actor
) {
}
