package br.com.qsoft.governance.aigateway.domain.attempt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tentativa durável de execução de IA (ADR-38/N05). Guarda <b>apenas
 * metadados</b>: nunca prompt, resposta, documento ou segredo.
 * <p>
 * Invariantes do schema V017: {@code estimated_cost} existe se e somente se
 * {@code cost_status = KNOWN}; uma tentativa {@code DENIED} é sempre
 * {@code NOT_STARTED}/{@code NOT_APPLICABLE}/{@code NOT_APPLICABLE}.
 */
public record RequestAttempt(
    UUID requestId,
    UUID tenantId,
    UUID userId,
    UUID departmentId,
    RequestPurpose purpose,
    UUID modelId,
    UUID providerId,
    AuthorizationStatus authorizationStatus,
    String denialReason,
    ExecutionStatus executionStatus,
    DeliveryStatus deliveryStatus,
    Long inputTokens,
    Long outputTokens,
    CostStatus costStatus,
    BigDecimal estimatedCost,
    BigDecimal unitPriceInput,
    BigDecimal unitPriceOutput,
    Long latencyMs,
    String errorCode,
    int deliveryAttempts,
    Instant nextDeliveryAt,
    Instant createdAt,
    Instant updatedAt
) {

    public RequestAttempt {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(authorizationStatus, "authorizationStatus");
        Objects.requireNonNull(executionStatus, "executionStatus");
        Objects.requireNonNull(deliveryStatus, "deliveryStatus");
        Objects.requireNonNull(costStatus, "costStatus");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if ((estimatedCost != null) != (costStatus == CostStatus.KNOWN)) {
            throw new IllegalArgumentException(
                "estimatedCost só existe quando costStatus = KNOWN.");
        }
    }

    /** Sucesso da chamada; {@code delivery} é PENDING (uso conhecido) ou NOT_APPLICABLE. */
    public RequestAttempt succeeded(Long inputTokens, Long outputTokens, CostStatus costStatus,
                                    BigDecimal estimatedCost, long latencyMs,
                                    DeliveryStatus deliveryStatus) {
        return new RequestAttempt(requestId, tenantId, userId, departmentId, purpose, modelId,
            providerId, authorizationStatus, denialReason, ExecutionStatus.SUCCEEDED, deliveryStatus,
            inputTokens, outputTokens, costStatus, estimatedCost, unitPriceInput, unitPriceOutput,
            latencyMs, null, deliveryAttempts, nextDeliveryAt, createdAt, Instant.now());
    }

    /** Falha técnica (timeout/erro). {@code costStatus} KNOWN só com cobrança declarada zero. */
    public RequestAttempt failed(ExecutionStatus executionStatus, CostStatus costStatus,
                                 BigDecimal estimatedCost, String errorCode, long latencyMs,
                                 DeliveryStatus deliveryStatus) {
        return new RequestAttempt(requestId, tenantId, userId, departmentId, purpose, modelId,
            providerId, authorizationStatus, denialReason, executionStatus, deliveryStatus,
            inputTokens, outputTokens, costStatus, estimatedCost, unitPriceInput, unitPriceOutput,
            latencyMs, errorCode, deliveryAttempts, nextDeliveryAt, createdAt, Instant.now());
    }

    /** Marca a entrega do evento de uso como concluída. */
    public RequestAttempt delivered() {
        return new RequestAttempt(requestId, tenantId, userId, departmentId, purpose, modelId,
            providerId, authorizationStatus, denialReason, executionStatus, DeliveryStatus.DELIVERED,
            inputTokens, outputTokens, costStatus, estimatedCost, unitPriceInput, unitPriceOutput,
            latencyMs, errorCode, deliveryAttempts, null, createdAt, Instant.now());
    }

    /** Reagenda a entrega após falha (incrementa as tentativas). */
    public RequestAttempt scheduledFor(Instant nextDeliveryAt) {
        return new RequestAttempt(requestId, tenantId, userId, departmentId, purpose, modelId,
            providerId, authorizationStatus, denialReason, executionStatus, DeliveryStatus.PENDING,
            inputTokens, outputTokens, costStatus, estimatedCost, unitPriceInput, unitPriceOutput,
            latencyMs, errorCode, deliveryAttempts + 1, nextDeliveryAt, createdAt, Instant.now());
    }

    /** Entrega esgotada: {@code DEAD} (mantém o contador já no limite). */
    public RequestAttempt dead() {
        return new RequestAttempt(requestId, tenantId, userId, departmentId, purpose, modelId,
            providerId, authorizationStatus, denialReason, executionStatus, DeliveryStatus.DEAD,
            inputTokens, outputTokens, costStatus, estimatedCost, unitPriceInput, unitPriceOutput,
            latencyMs, errorCode, deliveryAttempts, null, createdAt, Instant.now());
    }
}
