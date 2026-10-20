package br.com.qsoft.governance.chat.infrastructure.web;

import br.com.qsoft.governance.aigateway.api.completion.CompletionResult;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Recibo do turno (SPI-134), no formato da SPEC-04 §3. Os valores vêm direto
 * da completion; o chat não recalcula tokens nem custo.
 */
public record ReceiptResponse(
    UUID requestId,
    String modelo,
    String provider,
    Long tokensEntrada,
    Long tokensSaida,
    BigDecimal custoEstimado,
    String statusCusto,
    long latenciaMs
) {

    public static ReceiptResponse from(CompletionResult result) {
        return new ReceiptResponse(
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
