package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.aigateway.application.port.RequestAttemptRepository;
import br.com.qsoft.governance.aigateway.domain.attempt.AuthorizationStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.CostStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.DeliveryStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.ExecutionStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.RequestAttempt;
import br.com.qsoft.governance.aigateway.domain.attempt.RequestPurpose;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava a tentativa durável em transações próprias ({@code REQUIRES_NEW}):
 * DENIED, IN_FLIGHT (antes da chamada) e o resultado final (ADR-38).
 */
@Service
public class AttemptRecorder {

    private static final BigDecimal ZERO_COST = BigDecimal.ZERO.setScale(6);

    private final RequestAttemptRepository repository;

    public AttemptRecorder(RequestAttemptRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDenied(UUID requestId, UUID tenantId, UUID userId, UUID departmentId,
                             RequestPurpose purpose, UUID modelId, UUID providerId,
                             String denialReason) {
        Instant now = Instant.now();
        repository.save(new RequestAttempt(requestId, tenantId, userId, departmentId, purpose,
            modelId, providerId, AuthorizationStatus.DENIED, denialReason,
            ExecutionStatus.NOT_STARTED, DeliveryStatus.NOT_APPLICABLE, null, null,
            CostStatus.NOT_APPLICABLE, null, null, null, null, null, 0, null, now, now));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInFlight(UUID requestId, UUID tenantId, UUID userId, UUID departmentId,
                               RequestPurpose purpose, UUID modelId, UUID providerId,
                               BigDecimal unitPriceInput, BigDecimal unitPriceOutput) {
        Instant now = Instant.now();
        repository.save(new RequestAttempt(requestId, tenantId, userId, departmentId, purpose,
            modelId, providerId, AuthorizationStatus.GRANTED, null,
            ExecutionStatus.IN_FLIGHT, DeliveryStatus.NOT_APPLICABLE, null, null,
            CostStatus.NOT_APPLICABLE, null, unitPriceInput, unitPriceOutput, null, null, 0,
            null, now, now));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(UUID requestId, Long inputTokens, Long outputTokens,
                              CostStatus costStatus, BigDecimal estimatedCost, long latencyMs,
                              DeliveryStatus deliveryStatus) {
        repository.save(require(requestId).succeeded(inputTokens, outputTokens, costStatus,
            estimatedCost, latencyMs, deliveryStatus));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordTimeout(UUID requestId, long latencyMs) {
        repository.save(require(requestId).failed(ExecutionStatus.TIMED_OUT, CostStatus.INCONCLUSIVE,
            null, "TIMEOUT", latencyMs, DeliveryStatus.NOT_APPLICABLE));
    }

    /**
     * Falha técnica. Só há custo KNOWN (zero) quando o adaptador declara
     * explicitamente que não houve cobrança.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID requestId, String errorCode, long latencyMs,
                              boolean billingDeclaredZero) {
        if (billingDeclaredZero) {
            repository.save(require(requestId).failed(ExecutionStatus.FAILED, CostStatus.KNOWN,
                ZERO_COST, errorCode, latencyMs, DeliveryStatus.PENDING));
        } else {
            repository.save(require(requestId).failed(ExecutionStatus.FAILED,
                CostStatus.INCONCLUSIVE, null, errorCode, latencyMs, DeliveryStatus.NOT_APPLICABLE));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDelivered(UUID requestId) {
        repository.save(require(requestId).delivered());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void scheduleRetry(UUID requestId, Instant nextDeliveryAt) {
        repository.save(require(requestId).scheduledFor(nextDeliveryAt));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDead(UUID requestId) {
        repository.save(require(requestId).dead());
    }

    private RequestAttempt require(UUID requestId) {
        return repository.findById(requestId).orElseThrow(() -> new IllegalStateException(
            "Tentativa não encontrada: " + requestId));
    }
}
