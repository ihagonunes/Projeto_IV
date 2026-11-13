package br.com.qsoft.governance.aigateway.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.aigateway.domain.attempt.AuthorizationStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.CostStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.DeliveryStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.ExecutionStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.RequestAttempt;
import br.com.qsoft.governance.aigateway.domain.attempt.RequestPurpose;
import br.com.qsoft.governance.aigateway.api.exception.AIAttemptAccessDeniedException;
import br.com.qsoft.governance.aigateway.application.port.RequestAttemptRepository.AttemptFilter;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** Mapeamento e adapter da tentativa durável (V017/ADR-38). */
@ExtendWith(MockitoExtension.class)
class RequestAttemptPersistenceTest {

    @Mock
    private RequestAttemptJpaRepository jpaRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private RequestAttemptRepositoryAdapter adapter;

    @Test
    void searchBindsTenantScopeAndFiltersAndRestoresRetryTimestamp() {
        TenantId tenant = TenantId.random();
        UserId actor = UserId.random();
        UUID department = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        OffsetDateTime created = Instant.EPOCH.atOffset(ZoneOffset.UTC);
        OffsetDateTime retry = created.plusMinutes(5);
        var columns = Map.ofEntries(Map.entry("request_id", requestId), Map.entry("tenant_id", tenant.value()),
            Map.entry("user_id", actor.value()), Map.entry("department_id", department),
            Map.entry("purpose", "CHAT"), Map.entry("authorization_status", "GRANTED"),
            Map.entry("execution_status", "SUCCEEDED"), Map.entry("delivery_status", "PENDING"),
            Map.entry("cost_status", "INCONCLUSIVE"), Map.entry("delivery_attempts", 1),
            Map.entry("next_delivery_at", retry), Map.entry("created_at", created), Map.entry("updated_at", created));
        ResultSet row = mock(ResultSet.class, invocation -> columns.get(invocation.getArgument(0)));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(null);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<RequestAttempt>>any(),
            any(Object[].class))).thenAnswer(invocation ->
                List.of(invocation.<RowMapper<RequestAttempt>>getArgument(1).mapRow(row, 0)));
        var filter = new AttemptFilter(tenant, new VisibilityScope.SelfOnly(actor), null, null,
            ExecutionStatus.SUCCEEDED, null, null, actor.value(), department);

        var result = adapter.search(filter, 2, 10);

        assertThat(result.totalElements()).isZero();
        assertThat(result.content()).singleElement().satisfies(attempt -> {
            assertThat(attempt.requestId()).isEqualTo(requestId);
            assertThat(attempt.nextDeliveryAt()).isEqualTo(retry.toInstant());
        });
        ArgumentCaptor<String> sql = ArgumentCaptor.captor();
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<RequestAttempt>>any(),
            params.capture());
        assertThat(sql.getValue()).contains("tenant_id = ? AND user_id = ? AND execution_status = ?",
            "AND user_id = ? AND department_id = ?", "LIMIT ? OFFSET ?");
        assertThat(params.getValue()).containsExactly(tenant.value(), actor.value(), "SUCCEEDED",
            actor.value(), department, 10, 20L);
    }

    @Test
    void searchWithNoVisibilityFailsBeforeSql() {
        var filter = new AttemptFilter(TenantId.random(), new VisibilityScope.None(), null, null,
            null, null, null, null, null);

        assertThatThrownBy(() -> adapter.search(filter, 0, 10)).isInstanceOf(AIAttemptAccessDeniedException.class);
        verifyNoInteractions(jdbcTemplate, jpaRepository);
    }

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
