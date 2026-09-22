package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;

public record ChangeTenantStatusCommand(
    TenantId tenantId,
    TenantStatus newStatus
) {
}
