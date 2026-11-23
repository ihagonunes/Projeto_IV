package br.com.qsoft.governance.agents.infrastructure.web;

import br.com.qsoft.governance.aigateway.api.model.ReceiptInfo;
import java.math.BigDecimal;
import java.util.UUID;

/** Recibo público de uma etapa; nunca inclui prompt/resposta. */
public record ExecutionReceiptResponse(
    UUID requestId,
    UUID modelId,
    String modelName,
    String providerName,
    Long inputTokens,
    Long outputTokens,
    BigDecimal estimatedCost,
    String costStatus,
    Long latencyMs,
    String executionStatus
) {

    static ExecutionReceiptResponse from(ReceiptInfo receipt) {
        if (receipt == null) {
            return null;
        }
        return new ExecutionReceiptResponse(receipt.requestId(), receipt.modelId(),
            receipt.modelName(), receipt.providerName(), receipt.inputTokens(),
            receipt.outputTokens(), receipt.estimatedCost(), receipt.costStatus(),
            receipt.latencyMs(), receipt.executionStatus());
    }
}
