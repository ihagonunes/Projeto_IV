package br.com.qsoft.governance.aigateway.api.model;

import java.math.BigDecimal;
import java.util.UUID;

/** Metadados públicos de uma tentativa de IA, para o recibo durável do chat. */
public record ReceiptInfo(
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
}
