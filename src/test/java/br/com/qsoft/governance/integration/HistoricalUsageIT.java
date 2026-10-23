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

    @Autowired
    br.com.qsoft.governance.observability.infrastructure.query.HistoricalDashboardAdapter dashboard;

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

    @Test
    void dashboardScopesEveryQueryIncludingMetadataAndSelectedEmployee() {
        usage(tenantA, "D1", "2026-07-01T12:00:00", 3, 100, 100, 10);
        usage(tenantA, "D1", "2026-07-02T12:00:00", 5, 100, 100, 10);
        usage(tenantA, "D2", "2026-08-01T12:00:00", 90, 100, 100, 10);
        usage(tenantB, "D1", "2026-09-01T12:00:00", 999, 100, 100, 10);
        jdbcTemplate.update("UPDATE historical.ai_usage_logs SET employee_id=CASE WHEN request_count=5 THEN 'E2' ELSE 'E1' END");
        var self = new br.com.qsoft.governance.observability.application.port.HistoricalDashboardQuery.Scope(false, java.util.Set.of(), "E1");
        var f = new br.com.qsoft.governance.observability.application.port.HistoricalDashboardQuery.Filter(
            TenantId.of(tenantA), null, null, null, null, self);
        // E1 also has a D2 record: SELF sees both own records, never E2 or tenant B.
        assertThat(dashboard.series(f).stream().mapToLong(r -> r.requests()).sum()).isEqualTo(93);
        assertThat(dashboard.coverage(f).to()).isEqualTo(LocalDate.parse("2026-08-01"));
        assertThat(dashboard.activeUsers(f)).isEqualTo(1);
        var department = new br.com.qsoft.governance.observability.application.port.HistoricalDashboardQuery.Scope(false, java.util.Set.of("D1"), null);
        var selected = new br.com.qsoft.governance.observability.application.port.HistoricalDashboardQuery.Filter(
            TenantId.of(tenantA), null, null, null, null, department, "E2");
        assertThat(dashboard.series(selected).stream().mapToLong(r -> r.requests()).sum()).isEqualTo(5);
        var missing = new br.com.qsoft.governance.observability.application.port.HistoricalDashboardQuery.Scope(false, java.util.Set.of(), null);
        var denied = new br.com.qsoft.governance.observability.application.port.HistoricalDashboardQuery.Filter(
            TenantId.of(tenantA), null, null, null, null, missing);
        assertThat(dashboard.series(denied)).isEmpty();
        assertThat(dashboard.departments(denied)).isEmpty();
        assertThat(dashboard.models(denied)).isEmpty();
        assertThat(dashboard.coverage(denied).to()).isNull();
    }

    @Test
    void dashboardFiltersTenantAndIncludesWholeFinalDayWithUnknownPrices() {
        usage(tenantA, "D1", "2026-07-31T23:59:59.999999", 3, 1000, 500, 10);
        usage(tenantA, "D2", "2026-07-31T10:00:00", 7, 2000, 3000, 10);
        usage(tenantA, "D1", "2026-08-01T00:00:00", 99, 999, 999, 10);
        usage(tenantB, "D1", "2026-07-31T12:00:00", 999, 999, 999, 10);
        var f = new br.com.qsoft.governance.observability.application.port.HistoricalDashboardQuery.Filter(
            TenantId.of(tenantA), LocalDate.parse("2026-07-31"), LocalDate.parse("2026-07-31"), "D1", null);
        assertThat(dashboard.series(f)).singleElement().satisfies(row -> assertThat(row.requests()).isEqualTo(3));
        assertThat(dashboard.costs(f)).singleElement().satisfies(row -> {
            assertThat(row.estimatedCostUsd()).isNull();
            assertThat(row.unpricedRecords()).isEqualTo(1);
        });
        var foreign = new br.com.qsoft.governance.observability.application.port.HistoricalDashboardQuery.Filter(
            TenantId.of(UUID.randomUUID()), f.from(), f.to(), null, null);
        assertThat(dashboard.series(foreign)).isEmpty();
        assertThat(dashboard.models(foreign.tenantId())).isEmpty();
        assertThat(dashboard.departments(foreign.tenantId())).isEmpty();
    }

    @Test
    void dashboardUsesTokenTotalsWithoutMultiplyingByRequests() {
        String model = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO historical.ai_models(model_id,model_name,input_price_usd_per_1k_tokens,output_price_usd_per_1k_tokens) VALUES (?,?,?,?)", model, "Modelo", 2, 4);
        usage(tenantA, "D1", "2026-07-01T12:00:00", 20, 1000, 500, 10);
        jdbcTemplate.update("UPDATE historical.ai_usage_logs SET model_id = ?, employee_id = 'E1' WHERE company_id = ?", model, tenantA.toString());
        var f = new br.com.qsoft.governance.observability.application.port.HistoricalDashboardQuery.Filter(
            TenantId.of(tenantA), LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"), null, model);
        assertThat(dashboard.costs(f)).singleElement().satisfies(row -> {
            assertThat(row.requests()).isEqualTo(20);
            assertThat(row.estimatedCostUsd()).isEqualByComparingTo("4");
            assertThat(row.unpricedRecords()).isZero();
        });
        assertThat(dashboard.activeUsers(f)).isEqualTo(1);
        assertThat(dashboard.models(TenantId.of(tenantB))).isEmpty();
    }
}
