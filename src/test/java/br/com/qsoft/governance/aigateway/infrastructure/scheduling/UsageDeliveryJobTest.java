package br.com.qsoft.governance.aigateway.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.aigateway.application.AttemptRecorder;
import br.com.qsoft.governance.aigateway.application.UsageDeliveryPublisher;
import br.com.qsoft.governance.aigateway.application.port.RequestAttemptRepository;
import br.com.qsoft.governance.aigateway.domain.attempt.RequestAttempt;
import br.com.qsoft.governance.aigateway.infrastructure.config.AIDeliveryProperties;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UsageDeliveryJobTest {
    private final RequestAttemptRepository repository = mock(RequestAttemptRepository.class);
    private final AttemptRecorder recorder = mock(AttemptRecorder.class);
    private final UsageDeliveryPublisher publisher = mock(UsageDeliveryPublisher.class);
    private final AIDeliveryProperties properties = new AIDeliveryProperties();
    private final UsageDeliveryJob job = new UsageDeliveryJob(repository, recorder, publisher, properties);

    @Test
    void scheduledExecutionHonorsConfiguration() {
        properties.setEnabled(false);
        job.scheduledRun();
        verifyNoInteractions(repository);

        properties.setEnabled(true);
        job.scheduledRun();
        verify(repository).tenantsWithWork(any(), any());
    }

    @Test
    void skipsMissingClaimsAndMarksExhaustedDeliveriesDead() {
        UUID tenant = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        UUID retry = UUID.randomUUID();
        UUID exhausted = UUID.randomUUID();
        when(repository.tenantsWithWork(any(), any())).thenReturn(List.of(tenant));
        when(repository.claimDueDeliveries(eq(tenant), any(), any(), eq(50)))
            .thenReturn(List.of(gone, retry, exhausted));
        RequestAttempt pending = mock(RequestAttempt.class);
        RequestAttempt limit = mock(RequestAttempt.class);
        when(limit.deliveryAttempts()).thenReturn(properties.getMaxAttempts());
        when(repository.findById(retry)).thenReturn(Optional.of(pending));
        when(repository.findById(exhausted)).thenReturn(Optional.of(limit));

        assertThat(job.runOnce()).isEqualTo(2);
        verify(publisher).deliver(retry);
        verify(recorder).markDead(exhausted);
    }
}
