package br.com.qsoft.governance.aigateway.infrastructure.provider;

import br.com.qsoft.governance.aigateway.api.completion.CompletionMessage;
import br.com.qsoft.governance.aigateway.api.completion.ToolCall;
import br.com.qsoft.governance.aigateway.application.port.ProviderClient;
import br.com.qsoft.governance.aigateway.application.port.ProviderFailureException;
import br.com.qsoft.governance.aigateway.application.port.ProviderTimeoutException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Provider determinístico para testes e demo offline (ADR-38, SPEC-03 §2.3).
 * Comportamento definido pelo {@code external_name} do modelo.
 */
@Component
public class StubProviderClient implements ProviderClient {

    public static final String DEFAULT_MODE = "stub-echo";
    public static final String TIMEOUT_MODE = "stub-timeout";
    public static final String ERROR_MODE = "stub-error";
    public static final String UNKNOWN_USAGE_MODE = "stub-unknown-usage";
    public static final String TOOL_MODE = "stub-tool";
    public static final String TOOL_NAME = "buscar_politicas";

    private static final int CONTENT_MAX = 200;

    private final AtomicInteger toolCalls = new AtomicInteger();
    private volatile ProviderCall lastCall;

    @Override
    public ProviderResponse call(ProviderCall call) {
        this.lastCall = call;
        String mode = call.externalModelName() == null ? DEFAULT_MODE : call.externalModelName();
        switch (mode) {
            case TIMEOUT_MODE -> throw new ProviderTimeoutException();
            case ERROR_MODE -> throw new ProviderFailureException("HTTP_500");
            case UNKNOWN_USAGE_MODE -> {
                return new ProviderResponse(echo(call), List.of(), new Usage.Unknown(), false);
            }
            case TOOL_MODE -> {
                int sequence = toolCalls.incrementAndGet();
                if (sequence % 2 == 1) {
                    return new ProviderResponse(null,
                        List.of(new ToolCall(UUID.randomUUID().toString(), TOOL_NAME, "{}")),
                        new Usage.Known(tokens(call), 0), false);
                }
                return new ProviderResponse(echo(call), List.of(),
                    new Usage.Known(tokens(call), tokens(call)), false);
            }
            default -> {
                long wordCount = tokens(call);
                return new ProviderResponse(echo(call), List.of(),
                    new Usage.Known(wordCount, wordCount), false);
            }
        }
    }

    /** Última chamada recebida (para os testes verificarem o conteúdo enviado). */
    public ProviderCall lastCall() {
        return lastCall;
    }

    /** Reinicia o estado do modo {@code stub-tool} e a última chamada. */
    public void reset() {
        toolCalls.set(0);
        lastCall = null;
    }

    private static String echo(ProviderCall call) {
        String lastUser = lastUser(call);
        String truncated = lastUser.length() <= CONTENT_MAX
            ? lastUser : lastUser.substring(0, CONTENT_MAX);
        return "[stub] " + truncated;
    }

    private static long tokens(ProviderCall call) {
        String lastUser = lastUser(call).trim();
        return lastUser.isEmpty() ? 0 : lastUser.split("\\s+").length;
    }

    private static String lastUser(ProviderCall call) {
        return call.messages().stream()
            .filter(message -> "USER".equalsIgnoreCase(message.role()))
            .reduce((first, second) -> second)
            .map(CompletionMessage::content)
            .filter(Objects::nonNull)
            .orElse("");
    }
}
