package br.com.qsoft.governance.aigateway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.aigateway.application.port.RequestAttemptRepository;
import br.com.qsoft.governance.aigateway.domain.attempt.AuthorizationStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.CostStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.DeliveryStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.ExecutionStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.RequestAttempt;
import br.com.qsoft.governance.aigateway.domain.attempt.RequestPurpose;
import br.com.qsoft.governance.sharedkernel.event.AIRequestCompleted;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class AttemptDeliveryBoundariesTest {
    private final RequestAttemptRepository repository = mock(RequestAttemptRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AttemptRecorder recorder = new AttemptRecorder(repository);
    private final UsageDeliveryPublisher publisher = new UsageDeliveryPublisher(events, repository, recorder);

    @Test
    void absentAttemptsAreNotPublishedAndRecorderFailsExplicitly() {
        UUID id = UUID.randomUUID();

        publisher.deliver(id);
        verifyNoInteractions(events);
        assertThatThrownBy(() -> recorder.markDead(id))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining(id.toString());
    }

    @Test
    void legacyPendingAttemptDefaultsMissingEventMetricsAndMarksDelivered() {
        RequestAttempt pending = pending();
        when(repository.findById(pending.requestId())).thenReturn(Optional.of(pending));

        publisher.deliver(pending.requestId());

        ArgumentCaptor<AIRequestCompleted> event = ArgumentCaptor.captor();
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().estimatedCost()).isZero();
        assertThat(event.getValue().latencyMs()).isZero();
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(
            attempt -> attempt.deliveryStatus() == DeliveryStatus.DELIVERED));
    }

    @Test
    void recordsSuccessWithoutDiagnosticAndCanExhaustDelivery() {
        RequestAttempt pending = pending();
        when(repository.findById(pending.requestId())).thenReturn(Optional.of(pending));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        recorder.recordSuccess(pending.requestId(), 2L, 3L, CostStatus.KNOWN, BigDecimal.ONE, 10, DeliveryStatus.PENDING);
        recorder.markDead(pending.requestId());

        ArgumentCaptor<RequestAttempt> attempts = ArgumentCaptor.captor();
        verify(repository, org.mockito.Mockito.times(2)).save(attempts.capture());
        assertThat(attempts.getAllValues().getFirst().errorCode()).isNull();
        assertThat(attempts.getAllValues().getLast().deliveryStatus()).isEqualTo(DeliveryStatus.DEAD);
    }

    private static RequestAttempt pending() {
        return new RequestAttempt(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
            RequestPurpose.CHAT, UUID.randomUUID(), UUID.randomUUID(), AuthorizationStatus.GRANTED, null,
            ExecutionStatus.SUCCEEDED, DeliveryStatus.PENDING, null, null, CostStatus.INCONCLUSIVE,
            null, null, null, null, null, 0, null, Instant.EPOCH, Instant.EPOCH);
    }
}
