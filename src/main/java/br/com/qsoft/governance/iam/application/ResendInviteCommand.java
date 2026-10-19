package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;

/**
 * Dados de entrada do reenvio de convite (SPI-59). {@code actor} é quem reenvia
 * (precisa poder conceder o papel do convite).
 */
public record ResendInviteCommand(
    TenantId tenantId,
    InviteId inviteId,
    UserId actor
) {
}
