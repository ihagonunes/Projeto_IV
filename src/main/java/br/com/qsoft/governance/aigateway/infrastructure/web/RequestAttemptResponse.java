package br.com.qsoft.governance.aigateway.infrastructure.web;

import br.com.qsoft.governance.aigateway.domain.attempt.RequestAttempt;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Metadados de uma tentativa (RF047). Nunca expõe prompt, resposta, documento
 * ou segredo — esses dados não existem na tabela de tentativas.
 */
public record RequestAttemptResponse(
    UUID requestId,
    UUID userId,
    UUID departmentId,
    String purpose,
    UUID modelId,
    UUID providerId,
    String authorizationStatus,
    String denialReason,
    String executionStatus,
    String deliveryStatus,
    Long inputTokens,
    Long outputTokens,
    String costStatus,
    BigDecimal estimatedCost,
    BigDecimal unitPriceInput,
    BigDecimal unitPriceOutput,
    Long latencyMs,
    String errorCode,
    int deliveryAttempts,
    Instant createdAt,
    Instant updatedAt
) {

    public static RequestAttemptResponse from(RequestAttempt attempt) {
        return new RequestAttemptResponse(
            attempt.requestId(),
            attempt.userId(),
            attempt.departmentId(),
            attempt.purpose().name(),
            attempt.modelId(),
            attempt.providerId(),
            attempt.authorizationStatus().name(),
            attempt.denialReason(),
            attempt.executionStatus().name(),
            attempt.deliveryStatus().name(),
            attempt.inputTokens(),
            attempt.outputTokens(),
            attempt.costStatus().name(),
            attempt.estimatedCost(),
            attempt.unitPriceInput(),
            attempt.unitPriceOutput(),
            attempt.latencyMs(),
            attempt.errorCode(),
            attempt.deliveryAttempts(),
            attempt.createdAt(),
            attempt.updatedAt()
        );
    }
}
