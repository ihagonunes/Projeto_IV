package br.com.qsoft.governance.aigateway.infrastructure.web;

import br.com.qsoft.governance.aigateway.domain.provider.Provider;
import java.time.Instant;
import java.util.UUID;

/**
 * Representação pública de um Provider do catálogo. Expõe a <b>referência</b> da
 * credencial, nunca o segredo (ADR-10/ADR-38).
 */
public record ProviderResponse(
    UUID id,
    String name,
    String status,
    String kind,
    String baseUrl,
    String credentialRef,
    int timeoutMs,
    Instant createdAt
) {

    public static ProviderResponse from(Provider provider) {
        return new ProviderResponse(
            provider.id(),
            provider.name(),
            provider.status().name(),
            provider.kind().name(),
            provider.baseUrl(),
            provider.credentialRef(),
            provider.timeoutMs(),
            provider.createdAt()
        );
    }
}
