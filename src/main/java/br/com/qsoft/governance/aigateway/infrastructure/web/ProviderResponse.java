package br.com.qsoft.governance.aigateway.infrastructure.web;

import br.com.qsoft.governance.aigateway.domain.provider.Provider;
import java.time.Instant;
import java.util.UUID;

/** Representação pública de um Provider do catálogo. */
public record ProviderResponse(UUID id, String name, String status, Instant createdAt) {

    public static ProviderResponse from(Provider provider) {
        return new ProviderResponse(
            provider.id(),
            provider.name(),
            provider.status().name(),
            provider.createdAt()
        );
    }
}
