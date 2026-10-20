package br.com.qsoft.governance.aigateway.application.port;

import br.com.qsoft.governance.aigateway.api.completion.CompletionMessage;
import br.com.qsoft.governance.aigateway.api.completion.ToolCall;
import br.com.qsoft.governance.aigateway.api.completion.ToolSpec;
import br.com.qsoft.governance.aigateway.domain.provider.ProviderKind;
import java.util.List;
import java.util.Objects;

/**
 * Porta interna de provider (ADR-38): recebe a requisição já autorizada e
 * devolve conteúdo e uso. Não decide autorização.
 */
public interface ProviderClient {

    ProviderResponse call(ProviderCall call);

    /** Configuração e conteúdo da chamada (nunca contém segredo; só a referência). */
    record ProviderCall(
        ProviderKind kind,
        String baseUrl,
        String credentialRef,
        int timeoutMs,
        String externalModelName,
        List<CompletionMessage> messages,
        List<ToolSpec> tools
    ) {
        public ProviderCall {
            Objects.requireNonNull(kind, "kind é obrigatório.");
            Objects.requireNonNull(externalModelName, "externalModelName é obrigatório.");
            messages = messages == null ? List.of() : List.copyOf(messages);
            tools = tools == null ? List.of() : List.copyOf(tools);
        }
    }

    /** Resposta do provider; {@code usage} é KNOWN ou UNKNOWN. */
    record ProviderResponse(String content, List<ToolCall> toolCalls, Usage usage,
                            boolean billingDeclaredZero) {
        public ProviderResponse {
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
            Objects.requireNonNull(usage, "usage é obrigatório.");
        }
    }

    /** Uso devolvido pelo provider. */
    sealed interface Usage {
        record Known(long inputTokens, long outputTokens) implements Usage {
            public Known {
                if (inputTokens < 0 || outputTokens < 0) {
                    throw new IllegalArgumentException("tokens não podem ser negativos.");
                }
            }
        }

        record Unknown() implements Usage {
        }
    }
}
