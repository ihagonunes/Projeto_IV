package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.observability.application.port.HistoricalUsageQuery;
import br.com.qsoft.governance.observability.infrastructure.query.HistoricalUsageAdapter;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Teste de integração do read model do histórico (SPI-75): o schema
 * {@code historical} (dataset QSOFT, sem FK — ADR-26) é consultado de forma
 * <b>tenant-scoped</b> (o loader reescreve {@code company_id} para o tenant).
 * Exige Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = br.com.qsoft.governance.GovernanceApplication.class)
class HistoricalUsageIT {

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
    JdbcTemplate jdbcTemplate;

    @Autowired
    HistoricalUsageAdapter historicalUsageAdapter;

    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM historical.ai_usage_logs");
    }

    private void usage(UUID tenantId, String departmentId, String timestamp,
                       long requests, long input, long output, double latency) {
        jdbcTemplate.update("""
            INSERT INTO historical.ai_usage_logs
                (usage_id, "timestamp", company_id, department_id, request_count,
                 input_tokens, output_tokens, latency_ms, request_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'SUCCESS')
            """, UUID.randomUUID().toString(), timestamp, tenantId.toString(), departmentId,
            requests, input, output, latency);
    }

    private HistoricalUsageQuery.HistoricalUsageSummary summarize(
            UUID tenantId, String departmentId, LocalDate from, LocalDate to) {
        return historicalUsageAdapter.summarize(new HistoricalUsageQuery.HistoricalUsageFilter(
            TenantId.of(tenantId), departmentId, from, to));
    }

    @Test
    void shouldSummarizeOnlyTheRequestedTenant() {
        usage(tenantA, "DEPT-1", "2026-01-10T10:00:00", 10, 100, 200, 100.0);
        usage(tenantA, "DEPT-2", "2026-01-11T10:00:00", 5, 50, 60, 200.0);
        // Outro tenant — NÃO pode entrar no agregado (ADR-03).
        usage(tenantB, "DEPT-1", "2026-01-10T10:00:00", 999, 999, 999, 999.0);

        HistoricalUsageQuery.HistoricalUsageSummary summary = summarize(tenantA, null, null, null);

        assertThat(summary.requests()).isEqualTo(15);
        assertThat(summary.inputTokens()).isEqualTo(150);
        assertThat(summary.outputTokens()).isEqualTo(260);
    }

    @Test
    void shouldFilterByDepartmentAndPeriod() {
        usage(tenantA, "DEPT-1", "2026-01-10T10:00:00", 10, 100, 200, 100.0);
        usage(tenantA, "DEPT-2", "2026-01-11T10:00:00", 5, 50, 60, 200.0);
        usage(tenantA, "DEPT-1", "2026-02-01T10:00:00", 7, 70, 80, 50.0);

        assertThat(summarize(tenantA, "DEPT-1", null, null).requests()).isEqualTo(17);
        assertThat(summarize(tenantA, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
            .requests()).isEqualTo(15);
    }

    @Test
    void shouldReturnZeroWhenTenantHasNoHistoricalData() {
        usage(tenantA, "DEPT-1", "2026-01-10T10:00:00", 10, 100, 200, 100.0);

        HistoricalUsageQuery.HistoricalUsageSummary summary = summarize(UUID.randomUUID(), null, null, null);

        assertThat(summary.requests()).isZero();
        assertThat(summary.inputTokens()).isZero();
    }
}
