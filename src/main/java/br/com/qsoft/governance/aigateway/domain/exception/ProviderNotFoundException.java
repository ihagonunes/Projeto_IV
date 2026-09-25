package br.com.qsoft.governance.aigateway.domain.exception;

import java.util.UUID;

/** Provider não encontrado no tenant do contexto (404 — não revela cross-tenant). */
public class ProviderNotFoundException extends RuntimeException {

    public ProviderNotFoundException(UUID providerId) {
        super("Provider não encontrado: " + providerId + ".");
    }
}
