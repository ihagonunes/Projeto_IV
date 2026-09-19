package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;

public record ChangeUserStatusCommand(
    TenantId tenantId,
    UserId executorId,
    UserId targetUserId,
    UserStatus newStatus
) {
}
