package br.com.qsoft.governance.agentpolicies.infrastructure.web;

import br.com.qsoft.governance.aigateway.api.completion.CompletionResult;
import java.math.BigDecimal;
import java.util.UUID;

/** Recibo do agente, no mesmo formato do chat (SPEC-05 §4). */
public record AgentReceiptResponse(
    UUID requestId,
    String modelo,
    String provider,
    Long tokensEntrada,
    Long tokensSaida,
    BigDecimal custoEstimado,
    String statusCusto,
    long latenciaMs
) {

    public static AgentReceiptResponse from(CompletionResult result) {
        return new AgentReceiptResponse(
            result.requestId(),
            result.modelName(),
            result.providerName(),
            result.inputTokens(),
            result.outputTokens(),
            result.estimatedCost(),
            result.costStatus(),
            result.latencyMs()
        );
    }
}
