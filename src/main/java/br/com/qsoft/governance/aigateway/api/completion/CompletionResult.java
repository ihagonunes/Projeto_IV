package br.com.qsoft.governance.aigateway.api.completion;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Resultado de uma completion (SPEC-03 §2.1). {@code costStatus}: KNOWN |
 * NOT_APPLICABLE | INCONCLUSIVE. Nunca carrega prompt, resposta bruta de erro
 * ou segredo — apenas o conteúdo útil ao chamador.
 */
public record CompletionResult(
    UUID requestId,
    String content,
    List<ToolCall> toolCalls,
    UUID modelId,
    String modelName,
    UUID providerId,
    String providerName,
    Long inputTokens,
    Long outputTokens,
    BigDecimal estimatedCost,
    String costStatus,
    long latencyMs
) {

    public CompletionResult {
        Objects.requireNonNull(requestId, "requestId é obrigatório.");
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        Objects.requireNonNull(costStatus, "costStatus é obrigatório.");
    }
}
