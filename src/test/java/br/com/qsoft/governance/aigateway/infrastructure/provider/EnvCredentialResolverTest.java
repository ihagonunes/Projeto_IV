package br.com.qsoft.governance.aigateway.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.aigateway.application.port.ProviderFailureException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@link EnvCredentialResolver}: leitura de variável de ambiente, sem expor o valor. */
class EnvCredentialResolverTest {

    private final EnvCredentialResolver resolver = new EnvCredentialResolver();

    @Test
    void retornaValorConfiguradoERejeitaValorEmBranco() {
        var environment = Map.of("TEST_KEY", "synthetic-value", "EMPTY_KEY", "  ");
        var configured = new EnvCredentialResolver(environment::get);

        assertThat(configured.resolve("TEST_KEY")).isEqualTo("synthetic-value");
        assertThatThrownBy(() -> configured.resolve("EMPTY_KEY"))
            .isInstanceOfSatisfying(ProviderFailureException.class,
                ex -> assertThat(ex.errorCode()).isEqualTo("CREDENTIAL_MISSING"));
    }

    @Test
    void referenciaAusenteFalhaSemExporSegredo() {
        assertThatThrownBy(() -> resolver.resolve(null))
            .isInstanceOfSatisfying(ProviderFailureException.class,
                ex -> assertThat(ex.errorCode()).isEqualTo("CREDENTIAL_MISSING"));
        assertThatThrownBy(() -> resolver.resolve(" "))
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
