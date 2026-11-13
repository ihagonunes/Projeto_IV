package br.com.qsoft.governance.aigateway.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.aigateway.api.completion.CompletionMessage;
import br.com.qsoft.governance.aigateway.application.port.ProviderClient.ProviderCall;
import br.com.qsoft.governance.aigateway.application.port.ProviderClient.ProviderResponse;
import br.com.qsoft.governance.aigateway.application.port.ProviderClient.Usage;
import br.com.qsoft.governance.aigateway.application.port.ProviderFailureException;
import br.com.qsoft.governance.aigateway.application.port.ProviderTimeoutException;
import br.com.qsoft.governance.aigateway.domain.provider.ProviderKind;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Comportamento determinístico do {@link StubProviderClient} (SPEC-03 §2.3). */
class StubProviderClientTest {

    private StubProviderClient client;

    @BeforeEach
    void setUp() {
        client = new StubProviderClient();
        client.reset();
    }

    @Test
    void stubEchoDevolveUltimaMensagemDoUsuarioETokens() {
        ProviderResponse response = client.call(call("stub-echo", "uma duas tres"));

        assertThat(response.content()).isEqualTo("[stub] uma duas tres");
        assertThat(response.usage()).isInstanceOfSatisfying(Usage.Known.class, usage -> {
            assertThat(usage.inputTokens()).isEqualTo(3);
            assertThat(usage.outputTokens()).isEqualTo(3);
        });
        assertThat(client.lastCall()).isNotNull();
    }

    @Test
    void stubEchoTruncaConteudoLongo() {
        ProviderResponse response = client.call(call("stub-echo", "x".repeat(500)));

        assertThat(response.content()).hasSize("[stub] ".length() + 200);
    }

    @Test
    void stubEchoUsaUltimaMensagemDoUsuarioEIgnoraOutrosPapeis() {
        var request = new ProviderCall(ProviderKind.STUB, null, null, 30000,
            StubProviderClient.DEFAULT_MODE, List.of(CompletionMessage.user("anterior"),
                CompletionMessage.user("última pergunta"), CompletionMessage.system("instrução")), List.of());

        ProviderResponse response = client.call(request);

        assertThat(response.content()).isEqualTo("[stub] última pergunta");
        assertThat(response.usage()).isEqualTo(new Usage.Known(2, 2));
    }

    @Test
    void stubEchoSemMensagemDoUsuarioRetornaZeroTokens() {
        var request = new ProviderCall(ProviderKind.STUB, null, null, 30000,
            StubProviderClient.DEFAULT_MODE, List.of(CompletionMessage.system("instrução")), List.of());

        ProviderResponse response = client.call(request);

        assertThat(response.content()).isEqualTo("[stub] ");
        assertThat(response.usage()).isEqualTo(new Usage.Known(0, 0));
    }

    @Test
    void stubTimeoutLancaProviderTimeout() {
        assertThatThrownBy(() -> client.call(call("stub-timeout", "oi")))
            .isInstanceOf(ProviderTimeoutException.class);
    }

    @Test
    void stubErrorLancaProviderFailureComCodigoCurto() {
        assertThatThrownBy(() -> client.call(call("stub-error", "oi")))
            .isInstanceOfSatisfying(ProviderFailureException.class,
                ex -> assertThat(ex.errorCode()).isEqualTo("HTTP_500"));
    }

    @Test
    void stubUnknownUsageDevolveUsageDesconhecido() {
        ProviderResponse response = client.call(call("stub-unknown-usage", "oi"));

        assertThat(response.usage()).isInstanceOf(Usage.Unknown.class);
    }

    @Test
    void stubToolPedeFerramentaNaPrimeiraChamadaERespondeNaSegunda() {
        ProviderResponse first = client.call(call("stub-tool", "busque políticas"));
        ProviderResponse second = client.call(call("stub-tool", "busque políticas"));

        assertThat(first.content()).isNull();
        assertThat(first.toolCalls()).hasSize(1);
        assertThat(first.toolCalls().get(0).name()).isEqualTo(StubProviderClient.TOOL_NAME);
        assertThat(second.toolCalls()).isEmpty();
        assertThat(second.content()).contains("busque políticas");
    }

    private static ProviderCall call(String externalName, String userMessage) {
        return new ProviderCall(ProviderKind.STUB, null, null, 30000, externalName,
            List.of(CompletionMessage.user(userMessage)), List.of());
    }
}
