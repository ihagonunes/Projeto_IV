package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import br.com.qsoft.governance.GovernanceApplication;
import br.com.qsoft.governance.aigateway.infrastructure.scheduling.UsageDeliveryJob;
import br.com.qsoft.governance.observability.application.RecordUsage;
import br.com.qsoft.governance.observability.application.port.UsageRecordRepository;
import br.com.qsoft.governance.sharedkernel.event.AIRequestCompleted;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * TR04–TR07 (ADR-38/SPEC-03 §3.5–3.6): entrega durável, idempotência,
 * OUTCOME_UNKNOWN e DEAD. O job é desligado no teste e acionado via
 * {@link UsageDeliveryJob#runOnce()}.
 */
@Testcontainers
@SpringBootTest(classes = GovernanceApplication.class)
@Import(UsageDeliveryIT.FlakyConfig.class)
class UsageDeliveryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:18.6-alpine")
            .withDatabaseName("qsoft_test")
            .withUsername("qsoft")
            .withPassword("qsoft_test");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    UsageDeliveryJob job;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    FlakyRecordUsage flaky;

    @BeforeEach
    void reset() {
        flaky.reset();
    }

    // ── TR04 ──────────────────────────────────────────────────────────────────

    @Test
    void tr04_falhaTransitoriaDoListenerDeixaPendingEDepoisEntrega() {
        UUID requestId = UUID.randomUUID();
        insertAttempt(requestId, UUID.randomUUID(), "SUCCEEDED", "PENDING", 0,
            Instant.now().minusSeconds(1), Instant.now());
        flaky.failNext();

        assertThatCode(() -> job.runOnce()).doesNotThrowAnyException();
        assertThat(column(requestId, "delivery_status")).isEqualTo("PENDING");
        assertThat(attempts(requestId)).isEqualTo(1);
        assertThat(usageCount(requestId)).isZero();

        // força o vencimento da reentrega (backoff padrão começa em 60s)
        jdbcTemplate.update(
            "UPDATE aigateway_request_attempts SET next_delivery_at = now() - interval '1 second' "
                + "WHERE request_id = ?", requestId);

        job.runOnce();

        assertThat(column(requestId, "delivery_status")).isEqualTo("DELIVERED");
        assertThat(usageCount(requestId)).isEqualTo(1);
    }

    // ── TR05 ──────────────────────────────────────────────────────────────────

    @Test
    void tr05_reentregaDuplicadaNaoDuplicaUsageRecord() {
        UUID requestId = UUID.randomUUID();
        insertAttempt(requestId, UUID.randomUUID(), "SUCCEEDED", "PENDING", 0,
            Instant.now().minusSeconds(1), Instant.now());

        job.runOnce();
        assertThat(usageCount(requestId)).isEqualTo(1);

        // simula uma reentrega (linha voltou a PENDING com o mesmo request_id)
        jdbcTemplate.update("""
            UPDATE aigateway_request_attempts
               SET delivery_status = 'PENDING', next_delivery_at = now() - interval '1 second'
             WHERE request_id = ?
            """, requestId);

        job.runOnce();

        assertThat(usageCount(requestId)).isEqualTo(1);
        assertThat(column(requestId, "delivery_status")).isEqualTo("DELIVERED");
    }

    // ── TR06 ──────────────────────────────────────────────────────────────────

    @Test
    void tr06_inFlightExpiradoViraOutcomeUnknownInconclusivo() {
        UUID requestId = UUID.randomUUID();
        insertAttempt(requestId, UUID.randomUUID(), "IN_FLIGHT", "NOT_APPLICABLE", 0,
            null, Instant.now().minusSeconds(300));

        job.runOnce();

        assertThat(column(requestId, "execution_status")).isEqualTo("OUTCOME_UNKNOWN");
        assertThat(column(requestId, "cost_status")).isEqualTo("INCONCLUSIVE");
        assertThat(column(requestId, "delivery_status")).isEqualTo("NOT_APPLICABLE");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT estimated_cost FROM aigateway_request_attempts WHERE request_id = ?",
            BigDecimal.class, requestId)).isNull();
        assertThat(usageCount(requestId)).isZero();
    }

    // ── TR07 ──────────────────────────────────────────────────────────────────

    @Test
    void tr07_entregaEsgotadaViraDead() {
        UUID requestId = UUID.randomUUID();
        insertAttempt(requestId, UUID.randomUUID(), "SUCCEEDED", "PENDING", 8,
            Instant.now().minusSeconds(1), Instant.now());

        job.runOnce();

        assertThat(column(requestId, "delivery_status")).isEqualTo("DEAD");
        assertThat(usageCount(requestId)).isZero();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void insertAttempt(UUID requestId, UUID tenantId, String executionStatus,
                               String deliveryStatus, int deliveryAttempts,
                               Instant nextDeliveryAt, Instant createdAt) {
        boolean known = "PENDING".equals(deliveryStatus) || "DELIVERED".equals(deliveryStatus);
        jdbcTemplate.update("""
            INSERT INTO aigateway_request_attempts
                (request_id, tenant_id, user_id, department_id, purpose, model_id, provider_id,
                 authorization_status, denial_reason, execution_status, delivery_status,
                 input_tokens, output_tokens, cost_status, estimated_cost,
                 unit_price_input, unit_price_output, latency_ms, error_code,
                 delivery_attempts, next_delivery_at, created_at, updated_at)
            VALUES (?, ?, ?, NULL, 'CHAT', ?, ?, 'GRANTED', NULL, ?, ?,
                    10, 20, ?, ?, 0.01, 0.03, 120, NULL, ?, ?, ?, ?)
            """,
            requestId, tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            executionStatus, deliveryStatus,
            known ? "KNOWN" : "NOT_APPLICABLE",
            known ? new BigDecimal("0.000700") : null,
            deliveryAttempts,
            nextDeliveryAt == null ? null
                : OffsetDateTime.ofInstant(nextDeliveryAt, ZoneOffset.UTC),
            OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC),
            OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
    }

    private String column(UUID requestId, String column) {
        return jdbcTemplate.queryForObject(
            "SELECT " + column + " FROM aigateway_request_attempts WHERE request_id = ?",
            String.class, requestId);
    }

    private int attempts(UUID requestId) {
        return jdbcTemplate.queryForObject(
            "SELECT delivery_attempts FROM aigateway_request_attempts WHERE request_id = ?",
            Integer.class, requestId);
    }

    private int usageCount(UUID requestId) {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM observability_usage_records WHERE request_id = ?",
            Integer.class, requestId);
    }

    @TestConfiguration
    static class FlakyConfig {

        @Bean
        @Primary
        FlakyRecordUsage flakyRecordUsage(UsageRecordRepository repository) {
            return new FlakyRecordUsage(repository);
        }
    }

    /** Falha a próxima chamada de gravação para simular indisponibilidade transitória. */
    static class FlakyRecordUsage extends RecordUsage {

        private final AtomicInteger failures = new AtomicInteger();

        FlakyRecordUsage(UsageRecordRepository repository) {
            super(repository);
        }

        void failNext() {
            failures.set(1);
        }

        void reset() {
            failures.set(0);
        }

        @Override
        public boolean record(AIRequestCompleted event) {
            if (failures.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
                throw new IllegalStateException("falha transitória simulada");
            }
            return super.record(event);
        }
    }
}
