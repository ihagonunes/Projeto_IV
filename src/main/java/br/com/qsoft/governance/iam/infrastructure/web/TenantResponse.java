package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload de saída com a representação pública de um tenant,
 * incluindo dados cadastrais e identidade visual (white-label).
 */
public record TenantResponse(
    UUID id,
    String slug,
    String name,
    String status,
    String logoUrl,
    String primaryColor,
    String secondaryColor,
    Instant createdAt
) {

    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
            tenant.id().value(),
            tenant.slug(),
            tenant.name(),
            tenant.status().name(),
            tenant.logoUrl(),
            tenant.primaryColor(),
            tenant.secondaryColor(),
            tenant.createdAt()
        );
    }
}
