package br.com.qsoft.governance.aigateway.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.aigateway.domain.provider.ProviderKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProviderClientContractsTest {

    @Test
    void normalizesMissingProviderListsAndFailureCode() {
        var call = new ProviderClient.ProviderCall(ProviderKind.STUB, null, null, 1000, "stub", null, null);
        var response = new ProviderClient.ProviderResponse("Resposta", null, new ProviderClient.Usage.Unknown(), false);
        var failure = new ProviderFailureException(null, true);

        assertThat(call.messages()).isEmpty();
        assertThat(call.tools()).isEmpty();
        assertThat(response.toolCalls()).isEmpty();
        assertThat(failure.errorCode()).isEqualTo("PROVIDER_FAILURE");
        assertThat(failure.billingDeclaredZero()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"-1,0", "0,-1"})
    void rejectsNegativeUsage(long input, long output) {
        assertThatThrownBy(() -> new ProviderClient.Usage.Known(input, output))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("negativos");
    }
}
