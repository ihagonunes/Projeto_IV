package br.com.qsoft.governance.aigateway.infrastructure.scheduling;

import br.com.qsoft.governance.aigateway.application.AttemptRecorder;
import br.com.qsoft.governance.aigateway.application.UsageDeliveryPublisher;
import br.com.qsoft.governance.aigateway.application.port.RequestAttemptRepository;
import br.com.qsoft.governance.aigateway.domain.attempt.RequestAttempt;
import br.com.qsoft.governance.aigateway.infrastructure.config.AIDeliveryProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job de entrega durável de uso (ADR-38/SPEC-03 §3.5–3.6).
 * <p>
 * - reentrega as tentativas {@code PENDING} vencidas;
 * - passa para {@code DEAD} ao atingir {@code max-attempts} (log só do
 *   {@code request_id});
 * - converte {@code IN_FLIGHT} expirado em {@code OUTCOME_UNKNOWN}/{@code INCONCLUSIVE}.
 * <p>
 * Nos testes de integração o job é desligado por
 * {@code app.ai.delivery.enabled=false} e acionado com {@link #runOnce()}.
 */
@Component
public class UsageDeliveryJob {

    private static final Logger log = LoggerFactory.getLogger(UsageDeliveryJob.class);
    private static final int BATCH = 50;

    private final RequestAttemptRepository attemptRepository;
    private final AttemptRecorder attemptRecorder;
    private final UsageDeliveryPublisher deliveryPublisher;
    private final AIDeliveryProperties properties;

    public UsageDeliveryJob(RequestAttemptRepository attemptRepository,
                            AttemptRecorder attemptRecorder,
                            UsageDeliveryPublisher deliveryPublisher,
                            AIDeliveryProperties properties) {
        this.attemptRepository = attemptRepository;
        this.attemptRecorder = attemptRecorder;
        this.deliveryPublisher = deliveryPublisher;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.ai.delivery.interval-ms:30000}")
    public void scheduledRun() {
        if (!properties.isEnabled()) {
            return;
        }
        runOnce();
    }

    /** Executa uma rodada; devolve quantas entregas tentou. */
    public int runOnce() {
        Instant now = Instant.now();
        Instant staleBefore = now.minusMillis(properties.getInflightGraceMs());
        Instant leaseUntil = now.plusMillis(properties.getIntervalMs());
        int processed = 0;

        for (UUID tenantId : attemptRepository.tenantsWithWork(now, staleBefore)) {
            List<UUID> stale = attemptRepository.markStaleInFlight(
                tenantId, staleBefore, now, BATCH);
            for (UUID requestId : stale) {
                log.warn("AI attempt outcome unknown request_id={}", requestId);
            }

            for (UUID requestId : attemptRepository.claimDueDeliveries(
                tenantId, now, leaseUntil, BATCH)) {
                RequestAttempt attempt = attemptRepository.findById(requestId).orElse(null);
                if (attempt == null) {
                    continue;
                }
                if (attempt.deliveryAttempts() >= properties.getMaxAttempts()) {
                    attemptRecorder.markDead(requestId);
                    log.error("AI usage delivery exhausted request_id={}", requestId);
                } else {
                    deliveryPublisher.deliver(requestId);
                }
                processed++;
            }
        }
        return processed;
    }
}
