package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.sharedkernel.id.TenantId;

public record UpdateTenantDataCommand(
    TenantId tenantId,
    String name
) {
}
