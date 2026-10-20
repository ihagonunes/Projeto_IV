package br.com.qsoft.governance.aigateway.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.aigateway.domain.attempt.AuthorizationStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.CostStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.DeliveryStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.ExecutionStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.RequestAttempt;
import br.com.qsoft.governance.aigateway.domain.attempt.RequestPurpose;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Mapeamento e adapter da tentativa durável (V017/ADR-38). */
@ExtendWith(MockitoExtension.class)
class RequestAttemptPersistenceTest {

    @Mock
    private RequestAttemptJpaRepository jpaRepository;

    @InjectMocks
    private RequestAttemptRepositoryAdapter adapter;

    @Test
    void deve_converterEntreDominioEEntidade() {
        RequestAttempt attempt = sample();
        RequestAttemptJpaMapper mapper = new RequestAttemptJpaMapper();

        RequestAttemptJpaEntity entity = mapper.toEntity(attempt);
        RequestAttempt restored = mapper.toDomain(entity);

        assertThat(restored).isEqualTo(attempt);
        assertThat(entity.getTenantId()).isEqualTo(attempt.tenantId());
        assertThat(entity.getPurpose()).isEqualTo(RequestPurpose.CHAT);
        assertThat(entity.getCostStatus()).isEqualTo(CostStatus.KNOWN);
    }

    @Test
    void deve_salvarTentativa() {
        RequestAttempt attempt = sample();
        when(jpaRepository.save(any(RequestAttemptJpaEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        RequestAttempt saved = adapter.save(attempt);

        assertThat(saved).isEqualTo(attempt);
        verify(jpaRepository).save(any(RequestAttemptJpaEntity.class));
    }

    @Test
    void deve_buscarPorRequestId() {
        RequestAttempt attempt = sample();
        RequestAttemptJpaEntity entity = new RequestAttemptJpaMapper().toEntity(attempt);
        when(jpaRepository.findById(attempt.requestId())).thenReturn(Optional.of(entity));

        assertThat(adapter.findById(attempt.requestId())).contains(attempt);
    }

    @Test
    void deve_retornarVazio_quando_tentativaNaoExistir() {
        UUID requestId = UUID.randomUUID();
        when(jpaRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThat(adapter.findById(requestId)).isEmpty();
    }

    private static RequestAttempt sample() {
        Instant now = Instant.now();
        return new RequestAttempt(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), RequestPurpose.CHAT, UUID.randomUUID(), UUID.randomUUID(),
            AuthorizationStatus.GRANTED, null, ExecutionStatus.SUCCEEDED,
            DeliveryStatus.PENDING, 10L, 20L, CostStatus.KNOWN, new BigDecimal("0.000700"),
            new BigDecimal("0.010000"), new BigDecimal("0.030000"), 120L, null, 0, null,
            now, now);
    }
}
