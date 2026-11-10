package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import br.com.qsoft.governance.GovernanceApplication;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.support.TestTenants;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Orçamento mensal por departamento (SPEC-02 / POPROJ-54 / SPI-125):
 * upsert e histórico, isolamento de tenant, escopo por concessão, validação de
 * mês/intervalo, limites UTC do custo realizado e constraints da V016.
 * Exige Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = GovernanceApplication.class)
class DepartmentBudgetIT {

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

    private static final String PASSWORD = "secret123";

    @LocalServerPort
    int port;

    @Autowired
    TestTenants testTenants;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private RestClient client;
    private Tenant tenantA;
    private Tenant tenantB;
    private User adminA;
    private UUID departmentA;
    private UUID departmentB;
    private UUID departmentNoBudget;
    private UUID departmentOtherTenant;
    private String adminAEmail;
    private String adminBEmail;
    private String managerEmail;
    private String collaboratorEmail;
    private String executiveEmail;
    private String executiveBEmail;
    private User executiveA;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        String slug = "budget" + UUID.randomUUID().toString().substring(0, 8).toLowerCase();
        adminAEmail = "admin@" + slug + ".com";
        adminBEmail = "admin@" + slug + "b.com";
        managerEmail = "manager@" + slug + ".com";
        collaboratorEmail = "collab@" + slug + ".com";

        tenantA = testTenants.ensureTenant(slug, "Budget A", "Admin", adminAEmail, PASSWORD);
        tenantB = testTenants.ensureTenant(slug + "b", "Budget B", "Admin", adminBEmail, PASSWORD);
        adminA = testTenants.admin(tenantA, adminAEmail);
        executiveEmail = "exec@" + slug + ".com";
        executiveA = testTenants.ensureMember(tenantA, adminA.id(), executiveEmail, "Executive", PASSWORD,
            HierarchyLevel.EXECUTIVE, null, "EXECUTIVE");

        executiveBEmail = "exec@" + slug + "b.com";
        testTenants.ensureMember(tenantB, testTenants.admin(tenantB, adminBEmail).id(), executiveBEmail,
            "Executive B", PASSWORD, HierarchyLevel.EXECUTIVE, null, "EXECUTIVE");

        String tokenA = login(adminAEmail);
        departmentA = createDepartment(tokenA, "Marketing " + suffix(), null);
        departmentB = createDepartment(tokenA, "Vendas " + suffix(), "1500.00");
        departmentNoBudget = createDepartment(tokenA, "Operacoes " + suffix(), null);
        departmentOtherTenant = createDepartment(login(adminBEmail),
            "Outro Tenant " + suffix(), null);

        testTenants.ensureMember(tenantA, adminA.id(), managerEmail, "Manager", PASSWORD,
            HierarchyLevel.MANAGER, departmentA, "MANAGER");
        testTenants.ensureMember(tenantA, adminA.id(), collaboratorEmail, "Collab", PASSWORD,
            HierarchyLevel.COLLABORATOR, departmentA, "COLLABORATOR");
    }

    @Test
    void upsertEHistoricoDoOrcamento() {
        String token = login(executiveEmail);

        Map<String, Object> september = putBudget(token, departmentA, "2026-09", "900.00", 80);
        assertThat(september.get("mes")).isEqualTo("2026-09");
        assertThat(september.get("valor")).isNotNull();
        assertThat(september.get("limiarAlerta")).isEqualTo(80);
        assertThat(september.get("atualizadoPor")).isEqualTo(executiveA.id().value().toString());

        putBudget(token, departmentA, "2026-10", "1000.00", 80);
        Map<String, Object> updated = putBudget(token, departmentA, "2026-10", "1200.50", 90);
        assertThat(updated.get("mes")).isEqualTo("2026-10");
        assertThat(updated.get("limiarAlerta")).isEqualTo(90);

        List<Map<String, Object>> history = history(token, departmentA, "2026-09", "2026-10");
        assertThat(history).hasSize(2);
        assertThat(history).extracting(entry -> entry.get("mes"))
            .containsExactly("2026-09", "2026-10");
        assertThat(decimal(history.get(0).get("valor"))).isEqualByComparingTo("900.00");
        assertThat(decimal(history.get(1).get("valor"))).isEqualByComparingTo("1200.50");
        assertThat(history.get(1).get("limiarAlerta")).isEqualTo(90);
    }

    @Test
    void departamentoDeOutroTenantOuInexistenteRetorna404() {
        String token = login(executiveEmail);
        UUID missing = UUID.randomUUID();

        assertThat(putBudgetStatus(token, departmentOtherTenant, "2026-10", "1000.00"))
            .isEqualTo(404);
        assertThat(putBudgetStatus(token, missing, "2026-10", "1000.00")).isEqualTo(404);

        assertThat(historyStatus(token, departmentOtherTenant, "2026-01", "2026-12"))
            .isEqualTo(404);
        assertThat(historyStatus(token, missing, "2026-01", "2026-12")).isEqualTo(404);
    }

    @Test
    void managerVeApenasODepartamentoDaConcessao() {
        String admin = login(adminAEmail);
        putBudget(login(executiveEmail), departmentA, "2026-10", "1000.00", 80);
        putBudget(login(executiveEmail), departmentB, "2026-10", "2000.00", 80);

        String manager = login(managerEmail);
        assertThat(history(manager, departmentA, "2026-10", "2026-10")).hasSize(1);
        assertThat(historyStatus(manager, departmentB, "2026-10", "2026-10")).isEqualTo(403);
        assertThat(putBudgetStatus(manager, departmentA, "2026-10", "1500.00")).isEqualTo(403);

        List<Map<String, Object>> managerItems = budgetItems(manager, "2026-10");
        assertThat(managerItems).extracting(item -> item.get("departmentId"))
            .containsExactly(departmentA.toString());

        assertThat(budgetStatus(admin, "2026-10")).isEqualTo(403);
        assertThat(putBudgetStatus(admin, departmentA, "2026-10", "1500.00")).isEqualTo(403);
        List<Map<String, Object>> executiveItems = budgetItems(login(executiveEmail), "2026-10");
        assertThat(executiveItems).extracting(item -> item.get("departmentId"))
            .contains(departmentA.toString(), departmentB.toString());
    }

    @Test
    void collaboratorRecebe403() {
        String token = login(collaboratorEmail);

        assertThat(putBudgetStatus(token, departmentA, "2026-10", "1000.00")).isEqualTo(403);
        assertThat(historyStatus(token, departmentA, "2026-10", "2026-10")).isEqualTo(403);
        assertThat(budgetStatus(token, "2026-10")).isEqualTo(403);
    }

    @Test
    void mesInvalidoRetorna400() {
        String token = login(executiveEmail);

        assertThat(putBudgetStatus(token, departmentA, "2026-13", "1000.00")).isEqualTo(400);
        assertThat(historyStatus(token, departmentA, "2026-13", "2026-10")).isEqualTo(400);
        assertThat(budgetStatus(login(executiveEmail), "2026-13")).isEqualTo(400);
    }

    @Test
    void intervaloMaiorQue24MesesRetorna400() {
        String token = login(executiveEmail);

        assertThat(historyStatus(token, departmentA, "2024-01", "2026-01")).isEqualTo(400);
        assertThat(historyStatus(token, departmentA, "2024-01", "2025-12")).isEqualTo(200);
    }

    @Test
    void custoDoMesNaoVazaParaOMesSeguinte() {
        putBudget(login(executiveEmail), departmentA, "2026-10", "1000.00", 80);
        putBudget(login(executiveEmail), departmentA, "2026-11", "1000.00", 80);
        putBudget(login(executiveBEmail), departmentOtherTenant, "2026-10", "1000.00", 80);

        // Limites UTC: [2026-10-01T00:00Z, 2026-11-01T00:00Z).
        insertUsage(tenantA.id(), departmentA, "2026-09-30T23:59:59Z", "500.00");
        insertUsage(tenantA.id(), departmentA, "2026-10-01T00:00:00Z", "100.00");
        insertUsage(tenantA.id(), departmentA, "2026-10-31T23:59:59Z", "200.00");
        insertUsage(tenantA.id(), departmentA, "2026-11-01T00:00:00Z", "400.00");
        insertUsage(tenantB.id(), departmentOtherTenant, "2026-10-15T12:00:00Z", "5000.00");

        Map<String, Object> october = item(budgetReport(login(executiveEmail), "2026-10"), departmentA);
        assertThat(number(october.get("custoReal"))).isCloseTo(300.0, within(0.0001));
        assertThat(number(october.get("pctUsado"))).isCloseTo(30.0, within(0.0001));
        assertThat(october.get("status")).isEqualTo("OK");

        Map<String, Object> november = item(budgetReport(login(executiveEmail), "2026-11"), departmentA);
        assertThat(number(november.get("custoReal"))).isCloseTo(400.0, within(0.0001));
        assertThat(number(november.get("pctUsado"))).isCloseTo(40.0, within(0.0001));

        // Executivo A não vê o departamento nem o custo do tenant B.
        assertThat(budgetReport(login(executiveEmail), "2026-10"))
            .extracting(item -> item.get("departmentId"))
            .doesNotContain(departmentOtherTenant.toString());

        // Orçamento padrão do departamento (dept B, sem linha do mês) entra como default.
        Map<String, Object> defaultItem = item(budgetReport(login(executiveEmail), "2026-10"), departmentB);
        assertThat(number(defaultItem.get("orcamento"))).isCloseTo(1500.0, within(0.0001));
        assertThat(defaultItem.get("orcamentoPadrao")).isEqualTo(true);
        assertThat(defaultItem.get("limiarAlerta")).isEqualTo(80);
    }

    @Test
    void departamentoSemOrcamentoApareceComoSemOrcamento() {
        String admin = login(adminAEmail);
        insertUsage(tenantA.id(), departmentNoBudget, "2026-10-10T10:00:00Z", "42.00");

        Map<String, Object> item = item(budgetReport(login(executiveEmail), "2026-10"), departmentNoBudget);

        assertThat(item.get("orcamento")).isNull();
        assertThat(item.get("pctUsado")).isNull();
        assertThat(item.get("status")).isEqualTo("SEM_ORCAMENTO");
        assertThat(number(item.get("custoReal"))).isCloseTo(42.0, within(0.0001));
        assertThat(item.get("orcamentoPadrao")).isEqualTo(false);
    }

    @Test
    void flywayV016RejeitaMesForaDoDia1AmountNegativoELimiarZero() {
        assertThatThrownBy(() -> insertBudgetRow(LocalDate.of(2026, 10, 2),
            new BigDecimal("100.00"), 80))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_dmb_month");

        assertThatThrownBy(() -> insertBudgetRow(LocalDate.of(2027, 1, 1),
            new BigDecimal("-0.01"), 80))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_dmb_amount");

        assertThatThrownBy(() -> insertBudgetRow(LocalDate.of(2027, 2, 1),
            new BigDecimal("100.00"), 0))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_dmb_threshold");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String login(String email) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", PASSWORD))
            .retrieve().body(Map.class);
        return response == null ? null : (String) response.get("token");
    }

    private UUID createDepartment(String token, String name, String monthlyBudget) {
        Map<String, Object> body = monthlyBudget == null
            ? Map.of("name", name)
            : Map.of("name", name, "monthlyBudget", monthlyBudget);
        Map<String, Object> response = client.post().uri("/api/v1/departamentos")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve().body(new ParameterizedTypeReference<>() {
            });
        return UUID.fromString((String) response.get("id"));
    }

    private Map<String, Object> putBudget(String token, UUID departmentId, String month,
                                          String amount, int threshold) {
        return client.put().uri(budgetUri(departmentId, month))
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("amount", amount, "alertThresholdPct", threshold))
            .retrieve().body(new ParameterizedTypeReference<>() {
            });
    }

    private int putBudgetStatus(String token, UUID departmentId, String month, String amount) {
        try {
            client.put().uri(budgetUri(departmentId, month))
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", amount))
                .retrieve().toBodilessEntity();
            return 200;
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value();
        }
    }

    private String budgetUri(UUID departmentId, String month) {
        return "/api/v1/departamentos/" + departmentId + "/orcamentos/" + month;
    }

    private List<Map<String, Object>> history(String token, UUID departmentId,
                                              String from, String to) {
        return client.get().uri("/api/v1/departamentos/" + departmentId
                + "/orcamentos?de=" + from + "&ate=" + to)
            .header("Authorization", "Bearer " + token)
            .retrieve().body(new ParameterizedTypeReference<>() {
            });
    }

    private int historyStatus(String token, UUID departmentId, String from, String to) {
        try {
            history(token, departmentId, from, to);
            return 200;
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value();
        }
    }

    private List<Map<String, Object>> budgetItems(String token, String month) {
        return budgetReport(token, month);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> budgetReport(String token, String month) {
        Map<String, Object> response = client.get()
            .uri("/api/v1/observability/orcamentos?mes=" + month)
            .header("Authorization", "Bearer " + token)
            .retrieve().body(new ParameterizedTypeReference<>() {
            });
        return response == null ? List.of() : (List<Map<String, Object>>) response.get("itens");
    }

    private int budgetStatus(String token, String month) {
        try {
            budgetReport(token, month);
            return 200;
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value();
        }
    }

    private static Map<String, Object> item(List<Map<String, Object>> items, UUID departmentId) {
        return items.stream()
            .filter(item -> departmentId.toString().equals(item.get("departmentId")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Departamento ausente: " + departmentId));
    }

    private static double number(Object value) {
        return ((Number) value).doubleValue();
    }

    private static BigDecimal decimal(Object value) {
        return new BigDecimal(value.toString());
    }

    private void insertUsage(TenantId tenantId, UUID departmentId, String createdAt,
                             String estimatedCost) {
        jdbcTemplate.update("""
            INSERT INTO observability_usage_records
                (id, request_id, tenant_id, department_id, user_id, model_id, provider_id,
                 input_tokens, output_tokens, estimated_cost, latency_ms, status, created_at)
            VALUES (?, ?, ?, ?, NULL, NULL, NULL, 0, 0, ?, 0, 'COMPLETED', ?)
            """,
            UUID.randomUUID(), UUID.randomUUID(), tenantId.value(), departmentId,
            new BigDecimal(estimatedCost), Timestamp.from(Instant.parse(createdAt)));
    }

    private void insertBudgetRow(LocalDate month, BigDecimal amount, int threshold) {
        jdbcTemplate.update("""
            INSERT INTO department_monthly_budgets
                (id, tenant_id, department_id, month, amount, alert_threshold_pct,
                 updated_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(), tenantA.id().value(), departmentA, month, amount, threshold,
            adminA.id().value(),             Timestamp.from(Instant.now()),
            Timestamp.from(Instant.now()));
    }
}
