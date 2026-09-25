package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import java.util.UUID;

/**
 * Representação pública da identidade visual (white-label) do tenant (SPI-53).
 */
public record TenantBrandingResponse(
    UUID tenantId,
    String logoUrl,
    String primaryColor,
    String secondaryColor
) {

    public static TenantBrandingResponse from(Tenant tenant) {
        return new TenantBrandingResponse(
            tenant.id().value(),
            tenant.logoUrl(),
            tenant.primaryColor(),
            tenant.secondaryColor()
        );
    }
}
