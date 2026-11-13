package br.com.qsoft.governance.aigateway.domain.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestAttemptTest {

    @Test
    void enforcesKnownCostInvariantInBothDirections() {
        assertThatThrownBy(() -> attempt(CostStatus.KNOWN, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("estimatedCost");
        assertThatThrownBy(() -> attempt(CostStatus.INCONCLUSIVE, BigDecimal.ZERO))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("estimatedCost");
    }

    @Test
    void successWithoutDiagnosticAndExhaustedDeliveryPreserveAttemptData() {
        RequestAttempt started = attempt(CostStatus.INCONCLUSIVE, null);
        RequestAttempt completed = started.succeeded(5L, 8L, CostStatus.KNOWN, BigDecimal.ONE, 13,
            DeliveryStatus.PENDING);
        RequestAttempt dead = completed.scheduledFor(Instant.EPOCH.plusSeconds(60)).dead();

        assertThat(completed.executionStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(completed.errorCode()).isNull();
        assertThat(dead.requestId()).isEqualTo(started.requestId());
        assertThat(dead.deliveryStatus()).isEqualTo(DeliveryStatus.DEAD);
        assertThat(dead.deliveryAttempts()).isEqualTo(1);
        assertThat(dead.nextDeliveryAt()).isNull();
        assertThat(dead.estimatedCost()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(dead.inputTokens()).isEqualTo(5L);
    }

    private static RequestAttempt attempt(CostStatus costStatus, BigDecimal cost) {
        return new RequestAttempt(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
            RequestPurpose.CHAT, null, null, AuthorizationStatus.GRANTED, null,
            ExecutionStatus.NOT_STARTED, DeliveryStatus.NOT_APPLICABLE, null, null, costStatus,
            cost, null, null, null, null, 0, null, Instant.EPOCH, Instant.EPOCH);
    }
}
