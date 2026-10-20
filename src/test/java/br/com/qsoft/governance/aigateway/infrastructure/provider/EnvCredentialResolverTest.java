package br.com.qsoft.governance.aigateway.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.aigateway.application.port.ProviderFailureException;
import org.junit.jupiter.api.Test;

/** {@link EnvCredentialResolver}: leitura de variável de ambiente, sem expor o valor. */
class EnvCredentialResolverTest {

    private final EnvCredentialResolver resolver = new EnvCredentialResolver();

    @Test
    void referenciaAusenteFalhaSemExporSegredo() {
        assertThatThrownBy(() -> resolver.resolve(null))
            .isInstanceOfSatisfying(ProviderFailureException.class,
                ex -> assertThat(ex.errorCode()).isEqualTo("CREDENTIAL_MISSING"));
    }

    @Test
    void variavelInexistenteFalhaComCodigoCurto() {
        assertThatThrownBy(() -> resolver.resolve("QSOFT_NAO_EXISTE_TESTE"))
            .isInstanceOfSatisfying(ProviderFailureException.class,
                ex -> assertThat(ex.errorCode()).isEqualTo("CREDENTIAL_MISSING"));
    }
}
