package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.aigateway.application.port.RequestAttemptRepository;
import br.com.qsoft.governance.aigateway.domain.attempt.DeliveryStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.RequestAttempt;
import br.com.qsoft.governance.sharedkernel.event.AIRequestCompleted;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Entrega síncrona do {@link AIRequestCompleted} para uma tentativa
 * {@code PENDING} (ADR-38). Nesta task não há job de reentrega: uma falha
 * apenas registra {@code delivery_attempts} e {@code next_delivery_at}. A
 * exceção nunca chega ao cliente.
 */
@Service
public class UsageDeliveryPublisher {

    private static final Duration BASE_DELAY = Duration.ofSeconds(30);
    private static final Duration MAX_DELAY = Duration.ofHours(1);

    private final ApplicationEventPublisher eventPublisher;
    private final RequestAttemptRepository attemptRepository;
    private final AttemptRecorder recorder;

    public UsageDeliveryPublisher(ApplicationEventPublisher eventPublisher,
                                  RequestAttemptRepository attemptRepository,
                                  AttemptRecorder recorder) {
        this.eventPublisher = eventPublisher;
        this.attemptRepository = attemptRepository;
        this.recorder = recorder;
    }

    /** Publica o evento da tentativa, se ela estiver PENDING. */
    public void deliver(UUID requestId) {
        RequestAttempt attempt = attemptRepository.findById(requestId).orElse(null);
        if (attempt == null || attempt.deliveryStatus() != DeliveryStatus.PENDING) {
            return;
        }
        try {
            eventPublisher.publishEvent(toEvent(attempt));
            recorder.markDelivered(requestId);
        } catch (RuntimeException ex) {
            recorder.scheduleRetry(requestId, nextDeliveryAt(attempt.deliveryAttempts()));
        }
    }

    private static AIRequestCompleted toEvent(RequestAttempt attempt) {
        return new AIRequestCompleted(
            attempt.requestId(),
            attempt.tenantId(),
            attempt.departmentId(),
            attempt.userId(),
            attempt.modelId(),
            attempt.providerId(),
            attempt.inputTokens() == null ? 0 : attempt.inputTokens(),
            attempt.outputTokens() == null ? 0 : attempt.outputTokens(),
            attempt.estimatedCost() == null ? java.math.BigDecimal.ZERO : attempt.estimatedCost(),
            attempt.latencyMs() == null ? 0 : attempt.latencyMs(),
            "COMPLETED"
        );
    }

    private static Instant nextDeliveryAt(int attempts) {
        long seconds = BASE_DELAY.toSeconds() * (1L << Math.min(attempts + 1, 7));
        return Instant.now().plusSeconds(Math.min(seconds, MAX_DELAY.toSeconds()));
    }
}
