package br.com.qsoft.governance.aigateway.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import br.com.qsoft.governance.aigateway.application.port.ProviderFailureException;
import br.com.qsoft.governance.aigateway.domain.provider.ProviderKind;
import org.junit.jupiter.api.Test;

class ProviderClientRegistryAdapterTest {

    @Test
    void routesSupportedKindsAndRejectsMissingKind() {
        var stub = new StubProviderClient();
        var openAi = mock(OpenAiCompatibleProviderClient.class);
        var registry = new ProviderClientRegistryAdapter(stub, openAi);

        assertThat(registry.forKind(ProviderKind.STUB)).isSameAs(stub);
        assertThat(registry.forKind(ProviderKind.OPENAI_COMPATIBLE)).isSameAs(openAi);
        assertThatThrownBy(() -> registry.forKind(null))
            .isInstanceOfSatisfying(ProviderFailureException.class,
                failure -> assertThat(failure.errorCode()).isEqualTo("UNSUPPORTED_PROVIDER"));
    }
}
