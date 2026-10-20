package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.GovernanceApplication;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
 * SPI-101/F2: evidência de plano de execução das consultas de {@code /metrics/*}
 * e {@code /orcamentos} sobre massa sintética de 100 mil linhas operacionais.
 * <p>
 * O teste imprime {@code EXPLAIN (ANALYZE, BUFFERS)} de cada consulta. Só se
 * justifica criar índice novo quando o plano mostrar seq scan relevante; os
 * índices existentes (V013/V016) são a base. Exige Docker.
 */
@Testcontainers
@SpringBootTest(classes = GovernanceApplication.class)
class ObservabilityQueryPlanIT {

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

    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID DEPARTMENT =
        UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM observability_usage_records WHERE tenant_id = ?", TENANT);
        jdbcTemplate.update("DELETE FROM department_monthly_budgets WHERE tenant_id = ?", TENANT);
        jdbcTemplate.update("DELETE FROM department_departments WHERE tenant_id = ?", TENANT);

        jdbcTemplate.update("""
            INSERT INTO department_departments
                (id, tenant_id, name, status, created_at, updated_at)
            SELECT ('00000000-0000-0000-0000-' || lpad(d::text, 12, '0'))::uuid, ?,
                   'Dept ' || d, 'ACTIVE', now(), now()
              FROM generate_series(0, 4) AS d
            """, TENANT);

        jdbcTemplate.update("""
            INSERT INTO department_monthly_budgets
                (id, tenant_id, department_id, month, amount, alert_threshold_pct,
                 updated_by, created_at, updated_at)
            SELECT gen_random_uuid(), ?,
                   ('00000000-0000-0000-0000-' || lpad(d::text, 12, '0'))::uuid,
                   date_trunc('month', now())::date, 1000.00, 80, ?, now(), now()
              FROM generate_series(0, 4) AS d
            """, TENANT, UUID.randomUUID());

        jdbcTemplate.update("""
            INSERT INTO observability_usage_records
                (id, request_id, tenant_id, department_id, user_id, model_id, provider_id,
                 input_tokens, output_tokens, estimated_cost, latency_ms, status, created_at)
            SELECT gen_random_uuid(), gen_random_uuid(), ?,
                   ('00000000-0000-0000-0000-' || lpad((i % 5)::text, 12, '0'))::uuid,
                   ('10000000-0000-0000-0000-' || lpad((i % 20)::text, 12, '0'))::uuid,
                   ('20000000-0000-0000-0000-' || lpad((i % 3)::text, 12, '0'))::uuid,
                   ('30000000-0000-0000-0000-' || lpad((i % 3)::text, 12, '0'))::uuid,
                   100, 200, (i % 1000) / 100.0, 100, 'COMPLETED',
                   now() - ((i % 90) || ' days')::interval
              FROM generate_series(1, 100000) AS i
            """, TENANT);

        jdbcTemplate.execute("ANALYZE observability_usage_records");
        jdbcTemplate.execute("ANALYZE department_monthly_budgets");

        Long count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM observability_usage_records WHERE tenant_id = ?",
            Long.class, TENANT);
        assertThat(count).isEqualTo(100000L);
    }

    @Test
    void planosDasConsultasDoDashboardEOrcamento() {
        Instant to = Instant.now();
        Timestamp from = Timestamp.from(to.minus(Duration.ofDays(7)));
        Timestamp until = Timestamp.from(to);

        String costByDepartment = explain("metrics/costs groupBy=DEPARTMENT + departmentId",
            """
            SELECT department_id, COUNT(*), COALESCE(SUM(input_tokens), 0),
                   COALESCE(SUM(output_tokens), 0), COALESCE(SUM(estimated_cost), 0)
              FROM observability_usage_records
             WHERE tenant_id = ? AND department_id = ?
               AND created_at >= ? AND created_at < ?
             GROUP BY department_id
            """, TENANT, DEPARTMENT, from, until);

        String costByModel = explain("metrics/costs groupBy=MODEL + periodo",
            """
            SELECT model_id, COUNT(*), COALESCE(SUM(estimated_cost), 0)
              FROM observability_usage_records
             WHERE tenant_id = ? AND created_at >= ? AND created_at < ?
             GROUP BY model_id
            """, TENANT, from, until);

        String costByUser = explain("metrics/costs groupBy=USER + periodo",
            """
            SELECT user_id, COUNT(*), COALESCE(SUM(estimated_cost), 0)
              FROM observability_usage_records
             WHERE tenant_id = ? AND created_at >= ? AND created_at < ?
             GROUP BY user_id
            """, TENANT, from, until);

        String usageSeries = explain("metrics/usage serie por dia",
            """
            SELECT to_char(date_trunc('day', created_at), 'YYYY-MM-DD'), COUNT(*)
              FROM observability_usage_records
             WHERE tenant_id = ? AND created_at >= ? AND created_at < ?
             GROUP BY 1 ORDER BY 1
            """, TENANT, from, until);

        String budgetResolution = explain("orcamentos orcamento do mes (department)",
            """
            SELECT amount, alert_threshold_pct
              FROM department_monthly_budgets
             WHERE tenant_id = ? AND department_id = ?
               AND month = date_trunc('month', now())::date
            """, TENANT, DEPARTMENT);

        String budgetActualCost = explain("orcamentos custo real do mes (department)",
            """
            SELECT COALESCE(SUM(estimated_cost), 0)
              FROM observability_usage_records
             WHERE tenant_id = ? AND department_id = ?
               AND created_at >= ? AND created_at < ?
            """, TENANT, DEPARTMENT, from, until);

        assertThat(List.of(costByDepartment, costByModel, costByUser, usageSeries,
            budgetResolution, budgetActualCost)).allSatisfy(plan -> assertThat(plan).isNotBlank());
    }

    private String explain(String label, String sql, Object... params) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "EXPLAIN (ANALYZE, BUFFERS) " + sql, params);
        String plan = rows.stream()
            .map(row -> String.valueOf(row.values().iterator().next()))
            .reduce("", (left, right) -> left + right + System.lineSeparator());
        System.out.println("=== EXPLAIN: " + label + " ===");
        System.out.print(plan);
        return plan;
    }
}
