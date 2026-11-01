package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
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
 * Projeção determinística de orçamento (SPI-131/ADR-41/SPEC-09).
 * Cobre RBAC PJ07, validações PJ08, contrato e isolamento de tenant/departamento.
 * Exige Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = GovernanceApplication.class)
class BudgetProjectionIT {

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
    private UUID departmentOtherTenant;
    private String adminBEmail;
    private String managerEmail;
    private String collaboratorEmail;
    private String executiveEmail;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        String slug = "projection" + UUID.randomUUID().toString().substring(0, 8).toLowerCase();
        String adminAEmail = "admin@" + slug + ".com";
        adminBEmail = "admin@" + slug + "b.com";
        managerEmail = "manager@" + slug + ".com";
        collaboratorEmail = "collab@" + slug + ".com";
        executiveEmail = "exec@" + slug + ".com";

        tenantA = testTenants.ensureTenant(slug, "Projection A", "Admin", adminAEmail, PASSWORD);
        tenantB = testTenants.ensureTenant(slug + "b", "Projection B", "Admin", adminBEmail, PASSWORD);
        adminA = testTenants.admin(tenantA, adminAEmail);
        testTenants.ensureMember(tenantA, adminA.id(), executiveEmail, "Executive", PASSWORD,
            HierarchyLevel.EXECUTIVE, null, "EXECUTIVE");

        String adminAToken = login(adminAEmail);
        departmentA = createDepartment(adminAToken, "Marketing " + suffix());
        departmentB = createDepartment(adminAToken, "Vendas " + suffix());
        departmentOtherTenant = createDepartment(login(adminBEmail), "Outro " + suffix());

        testTenants.ensureMember(tenantA, adminA.id(), managerEmail, "Manager", PASSWORD,
            HierarchyLevel.MANAGER, departmentA, "MANAGER");
        testTenants.ensureMember(tenantA, adminA.id(), collaboratorEmail, "Collab", PASSWORD,
            HierarchyLevel.COLLABORATOR, departmentA, "COLLABORATOR");
    }

    @Test
    void pj07ManagerNaoVeOutroDepartamentoColaboradorNaoVeNemOutroTenantDa404() {
        String manager = login(managerEmail);
        assertThat(projectionStatus(manager, departmentB, currentMonth(), 14)).isEqualTo(403);

        String collaborator = login(collaboratorEmail);
        assertThat(projectionStatus(collaborator, departmentA, currentMonth(), 14)).isEqualTo(403);

        String executive = login(executiveEmail);
        assertThat(projectionStatus(executive, departmentOtherTenant, currentMonth(), 14))
            .isEqualTo(404);
        assertThat(projectionStatus(executive, departmentA, currentMonth(), 14)).isEqualTo(200);
    }

    @Test
    void pj08JanelaForaDoIntervaloEMesFuturoRetornam400() {
        String executive = login(executiveEmail);
        assertThat(projectionStatus(executive, departmentA, currentMonth(), 6)).isEqualTo(400);
        assertThat(projectionStatus(executive, departmentA, currentMonth(), 61)).isEqualTo(400);
        assertThat(projectionStatus(executive, departmentA,
            YearMonth.now(ZoneOffset.UTC).plusMonths(1).toString(), 14)).isEqualTo(400);
        assertThat(projectionStatus(executive, departmentA, currentMonth(), 7)).isEqualTo(200);
    }

    @Test
    void retornaContratoEIsolaCustoPorDepartamentoETenant() {
        String admin = loginAdminA();
        String month = currentMonth();
        putBudget(admin, departmentA, month, "0.00", 80);
        insertUsage(tenantA.id(), departmentA, Instant.now(), "10.00");
        insertUsage(tenantA.id(), departmentA, Instant.now(), "5.50");
        insertUsage(tenantA.id(), departmentB, Instant.now(), "99.00");
        insertUsage(tenantB.id(), departmentOtherTenant, Instant.now(), "888.00");

        Map<String, Object> response = projection(login(executiveEmail), departmentA, month, 7);

        assertThat(response.get("departmentId")).isEqualTo(departmentA.toString());
        assertThat(response.get("mes")).isEqualTo(month);
        assertThat(response.get("hoje")).isEqualTo(LocalDate.now(ZoneOffset.UTC).toString());
        assertThat(response.get("janelaDias")).isEqualTo(7);
        assertThat(number(response.get("orcamento"))).isZero();
        assertThat(response.get("orcamentoPadrao")).isEqualTo(false);
        assertThat(response.get("limiarAlerta")).isEqualTo(80);
        assertThat(number(response.get("gastoAteHoje"))).isCloseTo(15.50, within(0.0001));
        assertThat(decimal(response.get("mediaDiaria"))).isEqualByComparingTo("2.214286");
        assertThat(number(response.get("projetado"))).isGreaterThanOrEqualTo(15.50);
        assertThat(response.get("pctProjetado")).isNull();
        assertThat(response.get("diaEsgotamento")).isEqualTo(response.get("hoje"));
        assertThat(response.get("recomendacao")).isEqualTo("JA_EXCEDIDO");
        assertThat(response.get("tentativasInconclusivasNaJanela")).isNull();
        assertThat(response.get("calculationVersion")).isEqualTo("projection-v1");
    }

    @Test
    void mesPassadoDevolveRealizadoComZeroDiasRestantes() {
        String admin = loginAdminA();
        YearMonth previous = YearMonth.now(ZoneOffset.UTC).minusMonths(1);
        putBudget(admin, departmentA, previous.toString(), "1000.00", 80);
        insertUsage(tenantA.id(), departmentA,
            previous.atDay(10).atTime(12, 0).toInstant(ZoneOffset.UTC), "100.00");

        Map<String, Object> response = projection(login(executiveEmail), departmentA,
            previous.toString(), 7);

        assertThat(response.get("diasRestantes")).isEqualTo(0);
        assertThat(number(response.get("gastoAteHoje"))).isCloseTo(100.00, within(0.0001));
        assertThat(number(response.get("projetado"))).isCloseTo(100.00, within(0.0001));
        assertThat(number(response.get("pctProjetado"))).isCloseTo(10.0, within(0.0001));
        assertThat(response.get("diaEsgotamento")).isNull();
        assertThat(response.get("recomendacao")).isEqualTo("DENTRO_DO_ORCAMENTO");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String currentMonth() {
        return YearMonth.now(ZoneOffset.UTC).toString();
    }

    private String loginAdminA() {
        return login(adminA.email());
    }

    private String login(String email) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", PASSWORD))
            .retrieve().body(Map.class);
        return response == null ? null : (String) response.get("token");
    }

    private UUID createDepartment(String token, String name) {
        Map<String, Object> response = client.post().uri("/api/v1/departamentos")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("name", name))
            .retrieve().body(new ParameterizedTypeReference<>() {
            });
        return UUID.fromString((String) response.get("id"));
    }

    private void putBudget(String token, UUID departmentId, String month, String amount,
                           int threshold) {
        client.put().uri("/api/v1/departamentos/" + departmentId + "/orcamentos/" + month)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("amount", amount, "alertThresholdPct", threshold))
            .retrieve().toBodilessEntity();
    }

    private Map<String, Object> projection(String token, UUID departmentId, String month,
                                           int windowDays) {
        return client.get().uri("/api/v1/observability/orcamentos/projecao"
                + "?departmentId=" + departmentId + "&mes=" + month
                + "&janelaDias=" + windowDays)
            .header("Authorization", "Bearer " + token)
            .retrieve().body(new ParameterizedTypeReference<>() {
            });
    }

    private int projectionStatus(String token, UUID departmentId, String month, int windowDays) {
        try {
            projection(token, departmentId, month, windowDays);
            return 200;
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value();
        }
    }

    private static double number(Object value) {
        return ((Number) value).doubleValue();
    }

    private static BigDecimal decimal(Object value) {
        return new BigDecimal(value.toString());
    }

    private void insertUsage(TenantId tenantId, UUID departmentId, Instant createdAt,
                             String estimatedCost) {
        jdbcTemplate.update("""
            INSERT INTO observability_usage_records
                (id, request_id, tenant_id, department_id, user_id, model_id, provider_id,
                 input_tokens, output_tokens, estimated_cost, latency_ms, status, created_at)
            VALUES (?, ?, ?, ?, NULL, NULL, NULL, 0, 0, ?, 0, 'COMPLETED', ?)
            """,
            UUID.randomUUID(), UUID.randomUUID(), tenantId.value(), departmentId,
            new BigDecimal(estimatedCost), Timestamp.from(createdAt));
    }
}
