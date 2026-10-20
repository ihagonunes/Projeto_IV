package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.GovernanceApplication;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.support.TestTenants;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
 * RF047 (ADR-38/ADR-36): paginação, filtros e escopo de visibilidade das
 * tentativas. TR10 (manager só no departamento; outro tenant vazio) e TR07
 * (DEAD visível pelo endpoint).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = GovernanceApplication.class)
class AIGatewayAttemptsIT {

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
    private Tenant tenant;
    private Tenant other;
    private String adminEmail;
    private String managerEmail;
    private String collabEmail;
    private String otherAdminEmail;
    private UUID departmentA;
    private UUID departmentB;
    private UUID attemptA1;
    private UUID attemptA2;
    private UUID attemptA3;
    private UUID attemptOther;
    private Instant t0;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        String slug = "att" + UUID.randomUUID().toString().substring(0, 8).toLowerCase();
        adminEmail = "admin@" + slug + ".com";
        otherAdminEmail = "admin@" + slug + "x.com";
        tenant = testTenants.ensureTenant(slug, "Attempts Demo", "Admin", adminEmail, PASSWORD);
        other = testTenants.ensureTenant(slug + "x", "Attempts Other", "Admin",
            otherAdminEmail, PASSWORD);
        managerEmail = "manager@" + slug + ".com";
        collabEmail = "collab@" + slug + ".com";
        departmentA = UUID.randomUUID();
        departmentB = UUID.randomUUID();

        User admin = testTenants.admin(tenant, adminEmail);
        testTenants.ensureMember(tenant, admin.id(), managerEmail,
            "Manager", PASSWORD, HierarchyLevel.MANAGER, departmentA, "MANAGER");
        User collaborator = testTenants.ensureMember(tenant, admin.id(), collabEmail,
            "Collab", PASSWORD, HierarchyLevel.COLLABORATOR, departmentA, "COLLABORATOR");
        User otherAdmin = testTenants.admin(other, otherAdminEmail);

        t0 = Instant.now().minusSeconds(120);
        attemptA1 = insertAttempt(tenant, departmentA, collaborator.id().value(),
            "SUCCEEDED", "DELIVERED", new BigDecimal("0.000700"), 0, t0.plusSeconds(60));
        attemptA2 = insertAttempt(tenant, departmentB, admin.id().value(),
            "SUCCEEDED", "DELIVERED", new BigDecimal("0.000700"), 0, t0.plusSeconds(70));
        attemptA3 = insertAttempt(tenant, departmentA, collaborator.id().value(),
            "SUCCEEDED", "DEAD", new BigDecimal("0.000700"), 8, t0.plusSeconds(80));
        attemptOther = insertAttempt(other, departmentA, otherAdmin.id().value(),
            "SUCCEEDED", "DELIVERED", new BigDecimal("0.000700"), 0, t0.plusSeconds(90));
    }

    @Test
    void adminVeSomenteAsTentativasDoProprioTenant() {
        Map<String, Object> page = page(adminEmail, "");

        assertThat(ids(page)).containsExactlyInAnyOrder(
            attemptA1.toString(), attemptA2.toString(), attemptA3.toString());
        assertThat(ids(page)).doesNotContain(attemptOther.toString());
        assertThat(page.get("totalElements")).isEqualTo(3);
    }

    @Test
    void tr10_managerVeSomenteODepartamentoEOutroTenantFicaVazio() {
        Map<String, Object> page = page(managerEmail, "");

        assertThat(ids(page)).containsExactlyInAnyOrder(
            attemptA1.toString(), attemptA3.toString());
        assertThat(ids(page)).doesNotContain(attemptA2.toString(), attemptOther.toString());

        assertThat(status(managerEmail, "?departmentId=" + departmentB)).isEqualTo(403);

        Map<String, Object> otherPage = page(otherAdminEmail, "");
        assertThat(ids(otherPage)).containsExactly(attemptOther.toString());
    }

    @Test
    void tr07_filtroPorDeliveryStatusDeadDevolveATentativaEsgotada() {
        Map<String, Object> page = page(adminEmail, "?deliveryStatus=DEAD");

        assertThat(ids(page)).containsExactly(attemptA3.toString());
        assertThat(page.get("totalElements")).isEqualTo(1);

        Map<String, Object> managerPage = page(managerEmail, "?deliveryStatus=DEAD");
        assertThat(ids(managerPage)).containsExactly(attemptA3.toString());
    }

    @Test
    void colaboradorVeSomenteOProprioUsoENaoFiltraOutroUsuario() {
        Map<String, Object> page = page(collabEmail, "");

        assertThat(ids(page)).containsExactlyInAnyOrder(
            attemptA1.toString(), attemptA3.toString());
        assertThat(ids(page)).doesNotContain(attemptA2.toString());

        User admin = testTenants.admin(tenant, adminEmail);
        assertThat(status(collabEmail, "?userId=" + admin.id().value())).isEqualTo(403);
    }

    @Test
    void paginacaoZeroBasedComTotalCorreto() {
        Map<String, Object> first = page(adminEmail, "?page=0&size=2");
        assertThat((List<?>) first.get("content")).hasSize(2);
        assertThat(first.get("totalElements")).isEqualTo(3);
        assertThat(first.get("totalPages")).isEqualTo(2);

        Map<String, Object> second = page(adminEmail, "?page=1&size=2");
        assertThat((List<?>) second.get("content")).hasSize(1);
    }

    @Test
    void filtroDePeriodoRestringeAsTentativas() {
        String from = t0.plusSeconds(50).toString();
        String to = t0.plusSeconds(65).toString();

        Map<String, Object> page = page(adminEmail,
            "?de=" + from + "&ate=" + to);

        assertThat(ids(page)).containsExactly(attemptA1.toString());
    }

    @Test
    void respostaTrazApenasMetadados() {
        Map<String, Object> page = page(adminEmail, "");

        @SuppressWarnings("unchecked")
        Map<String, Object> item = ((List<Map<String, Object>>) page.get("content")).get(0);
        assertThat(item)
            .containsKeys("requestId", "executionStatus", "deliveryStatus", "costStatus")
            .doesNotContainKeys("content", "prompt", "response", "secret", "messages");
    }

    @Test
    void rejeitaSemAutenticacao() {
        try {
            client.get().uri("/api/v1/ai-gateway/tentativas").retrieve().toBodilessEntity();
            assertThat(false).as("deveria retornar 401").isTrue();
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(401);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> page(String email, String query) {
        return client.get().uri("/api/v1/ai-gateway/tentativas" + query)
            .header("Authorization", "Bearer " + login(email))
            .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {
            });
    }

    private int status(String email, String query) {
        try {
            client.get().uri("/api/v1/ai-gateway/tentativas" + query)
                .header("Authorization", "Bearer " + login(email))
                .retrieve().toBodilessEntity();
            return 200;
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value();
        }
    }

    private String login(String email) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", PASSWORD))
            .retrieve().body(Map.class);
        return response == null ? null : (String) response.get("token");
    }

    @SuppressWarnings("unchecked")
    private static List<String> ids(Map<String, Object> page) {
        return ((List<Map<String, Object>>) page.get("content")).stream()
            .map(item -> (String) item.get("requestId"))
            .toList();
    }

    private UUID insertAttempt(Tenant owner, UUID departmentId, UUID userId,
                               String executionStatus, String deliveryStatus,
                               BigDecimal cost, int deliveryAttempts, Instant createdAt) {
        UUID requestId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO aigateway_request_attempts
                (request_id, tenant_id, user_id, department_id, purpose, model_id, provider_id,
                 authorization_status, denial_reason, execution_status, delivery_status,
                 input_tokens, output_tokens, cost_status, estimated_cost,
                 unit_price_input, unit_price_output, latency_ms, error_code,
                 delivery_attempts, next_delivery_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'CHAT', ?, ?, 'GRANTED', NULL, ?, ?,
                    10, 20, 'KNOWN', ?, 0.01, 0.03, 100, NULL, ?, NULL, ?, ?)
            """,
            requestId, owner.id().value(), userId, departmentId,
            UUID.randomUUID(), UUID.randomUUID(),
            executionStatus, deliveryStatus, cost, deliveryAttempts,
            OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC),
            OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
        return requestId;
    }
}
