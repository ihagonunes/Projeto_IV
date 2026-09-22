package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.UUID;

public record UpdateUserProfileCommand(
    TenantId tenantId,
    UserId targetUserId,
    UUID departmentId,
    HierarchyLevel hierarchyLevel,
    Boolean isAdmin
) {
}
