package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;

/**
 * Dados de entrada da revogação de convite.
 */
public record RevokeInviteCommand(
    TenantId tenantId,
    InviteId inviteId
) {
}
