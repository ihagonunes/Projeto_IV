package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.sharedkernel.id.TenantId;

public record UpdateTenantBrandingCommand(
    TenantId tenantId,
    String logoUrl,
    String primaryColor,
    String secondaryColor
) {
}
