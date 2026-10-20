package br.com.qsoft.governance.aigateway.infrastructure.provider;

import br.com.qsoft.governance.aigateway.application.port.CredentialResolver;
import br.com.qsoft.governance.aigateway.application.port.ProviderFailureException;
import org.springframework.stereotype.Component;

/**
 * Resolve a credencial do provider a partir de variável de ambiente
 * ({@code credentialRef}). Variável ausente → {@code CREDENTIAL_MISSING}.
 * Substituível em testes (bean alternativo).
 */
@Component
public class EnvCredentialResolver implements CredentialResolver {

    @Override
    public String resolve(String credentialRef) {
        if (credentialRef == null || credentialRef.isBlank()) {
            throw new ProviderFailureException("CREDENTIAL_MISSING");
        }
        String value = System.getenv(credentialRef);
        if (value == null || value.isBlank()) {
            throw new ProviderFailureException("CREDENTIAL_MISSING");
        }
        return value;
    }
}
