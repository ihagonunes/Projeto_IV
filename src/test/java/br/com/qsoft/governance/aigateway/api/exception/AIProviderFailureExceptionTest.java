package br.com.qsoft.governance.aigateway.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AIProviderFailureExceptionTest {
    @Test
    void missingDiagnosticCodeUsesGenericProviderFailure() {
        var failure = new AIProviderFailureException(null);

        assertThat(failure.errorCode()).isEqualTo("PROVIDER_FAILURE");
        assertThat(failure).hasMessage("O provider de IA falhou.");
    }
}
