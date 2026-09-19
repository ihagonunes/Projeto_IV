package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;

public record ResendUserInviteCommand(
    TenantId tenantId,
    UserId targetUserId
) {
}
