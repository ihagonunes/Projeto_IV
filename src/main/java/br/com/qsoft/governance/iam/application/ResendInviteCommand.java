package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;

/**
 * Dados de entrada do reenvio de convite (SPI-59).
 */
public record ResendInviteCommand(
    TenantId tenantId,
    InviteId inviteId
) {
}
