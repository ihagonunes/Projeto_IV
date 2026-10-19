package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.aigateway.application.CompleteAIRequest;
import br.com.qsoft.governance.aigateway.application.CompleteAIRequestCommand;
import br.com.qsoft.governance.aigateway.application.CreateModel;
import br.com.qsoft.governance.aigateway.application.CreateModelCommand;
import br.com.qsoft.governance.aigateway.application.CreateProvider;
import br.com.qsoft.governance.aigateway.application.CreateProviderCommand;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.support.TestTenants;
import java.math.BigDecimal;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Teste de integração do dashboard (SPI-88…96): agregações sobre o operacional,
 * filtros combinados de período/departamento (SPI-90/91) e <b>enforcement de
 * escopo</b> no backend (SPI-94 — o frontend não substitui o RBAC). Exige Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = br.com.qsoft.governance.GovernanceApplication.class)
class DashboardMetricsIT {

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

    private static final String SLUG = "dashdemo";
    private static final String ADMIN_EMAIL = "admin@dashdemo.com";
    private static final String MANAGER_EMAIL = "manager@dashdemo.com";
    private static final String COLLAB_EMAIL = "collab@dashdemo.com";
    private static final String IMPOSTOR_EMAIL = "impostor@dashdemo.com";
    private static final String PASSWORD = "secret123";

    @LocalServerPort
    int port;

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CreateProvider createProvider;

    @Autowired
    CreateModel createModel;

    @Autowired
    CompleteAIRequest completeAIRequest;

    @Autowired
    TestTenants testTenants;

    private RestClient client;
    private Tenant tenant;
    private UUID marketingDepartment;
    private UUID salesDepartment;
    private UUID modelId;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        tenant = ensureTenant();
        marketingDepartment = UUID.randomUUID();
        salesDepartment = UUID.randomUUID();

        UUID providerId = createProvider.create(new CreateProviderCommand(
            tenant.id(), "OpenAI " + UUID.randomUUID())).id();
        Model model = createModel.create(new CreateModelCommand(
            tenant.id(), providerId, "gpt-4 " + UUID.randomUUID(),
            new BigDecimal("0.010000"), new BigDecimal("0.030000")));
        modelId = model.id();
    }

    private Tenant ensureTenant() {
        return testTenants.ensureTenant(SLUG, "Dash Demo", "Admin", ADMIN_EMAIL, PASSWORD);
    }

    private String login(String email) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", PASSWORD))
            .retrieve().body(Map.class);
        return response == null ? null : (String) response.get("token");
    }

    /** Emite uso para um usuário/departamento via o pipeline real do Gateway. */
    private void usage(User user, UUID departmentId, long input, long output) {
        completeAIRequest.complete(new CompleteAIRequestCommand(
            UUID.randomUUID(), tenant.id(), departmentId, user.id().value(),
            modelId, input, output, 100, "COMPLETED"));
    }

    private Map<?, ?> getJson(String token, String uri) {
        return client.get().uri(uri)
            .header("Authorization", "Bearer " + token)
            .retrieve().body(Map.class);
    }

    private int status(String token, String uri) {
        try {
            client.get().uri(uri)
                .header("Authorization", "Bearer " + token)
                .retrieve().toBodilessEntity();
            return 200;
        } catch (RestClientResponseException e) {
            return e.getStatusCode().value();
        }
    }

    @Test
    void shouldAggregateUsageAndCostByPeriodAndDepartment() {
        User admin = userRepository.findByTenantIdAndEmail(tenant.id(), ADMIN_EMAIL).orElseThrow();
        usage(admin, marketingDepartment, 1000, 2000);
        usage(admin, salesDepartment, 500, 500);

        String token = login(ADMIN_EMAIL);
        Map<?, ?> usageMetrics = getJson(token, "/api/v1/observability/metrics/usage");
        assertThat(usageMetrics.get("activeUsers")).isNotNull();
        assertThat((List<?>) usageMetrics.get("series")).isNotEmpty();

        List<Map<String, Object>> costs = client.get()
            .uri("/api/v1/observability/metrics/costs")
            .header("Authorization", "Bearer " + token)
            .retrieve().body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
            });
        assertThat(costs).isNotEmpty();
        assertThat(costs).anySatisfy(cost ->
            assertThat(cost.get("departmentId")).isEqualTo(marketingDepartment.toString()));
    }

    @Test
    void shouldFilterCostsByDepartment() {
        User admin = userRepository.findByTenantIdAndEmail(tenant.id(), ADMIN_EMAIL).orElseThrow();
        usage(admin, marketingDepartment, 1000, 2000);
        usage(admin, salesDepartment, 500, 500);

        List<Map<String, Object>> costs = client.get()
            .uri("/api/v1/observability/metrics/costs?departmentId=" + marketingDepartment)
            .header("Authorization", "Bearer " + login(ADMIN_EMAIL))
            .retrieve().body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
            });

        assertThat(costs).isNotEmpty();
        assertThat(costs).allSatisfy(cost ->
            assertThat(cost.get("departmentId")).isEqualTo(marketingDepartment.toString()));
    }

    @Test
    void managerShouldSeeOnlyOwnDepartmentEvenRequestingAnother() {
        User admin = userRepository.findByTenantIdAndEmail(tenant.id(), ADMIN_EMAIL).orElseThrow();
        // Gestor coerente: concessão MANAGER/DEPARTMENT(marketing) e rótulo MANAGER.
        testTenants.ensureMember(tenant, admin.id(), MANAGER_EMAIL, "User", PASSWORD,
            HierarchyLevel.MANAGER, marketingDepartment, "MANAGER");
        usage(admin, marketingDepartment, 1000, 2000);
        usage(admin, salesDepartment, 500, 500);

        // O gestor PEDE o departamento de vendas, mas o backend força o dele (SPI-94):
        // o escopo vem da concessão (visibilityFor), nunca do rótulo (ADR-36 item 9).
        List<Map<String, Object>> costs = client.get()
            .uri("/api/v1/observability/metrics/costs?departmentId=" + salesDepartment)
            .header("Authorization", "Bearer " + login(MANAGER_EMAIL))
            .retrieve().body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
            });

        assertThat(costs).allSatisfy(cost ->
            assertThat(cost.get("departmentId")).isEqualTo(marketingDepartment.toString()));
    }

    @Test
    void collaboratorShouldSeeOnlyOwnUsage() {
        User admin = userRepository.findByTenantIdAndEmail(tenant.id(), ADMIN_EMAIL).orElseThrow();
        User collaborator = testTenants.ensureMember(tenant, admin.id(), COLLAB_EMAIL, "User",
            PASSWORD, HierarchyLevel.COLLABORATOR, marketingDepartment, "COLLABORATOR");
        usage(admin, marketingDepartment, 1000, 2000);
        usage(collaborator, marketingDepartment, 10, 20);

        Map<?, ?> usageMetrics = getJson(login(COLLAB_EMAIL), "/api/v1/observability/metrics/usage");

        // SELF: apenas o próprio uso — 1 usuário ativo e 1 requisição no total.
        assertThat(usageMetrics.get("activeUsers")).isEqualTo(1);
        long total = ((List<Map<String, Object>>) usageMetrics.get("series")).stream()
            .mapToLong(point -> ((Number) point.get("requests")).longValue())
            .sum();
        assertThat(total).isEqualTo(1);
    }

    /**
     * Regressão de segurança (revisão A1–A6): rótulo EXECUTIVE com concessão
     * COLLABORATOR/SELF. O escopo de visibilidade tem que vir da concessão
     * (ADR-36 item 9) — o rótulo NUNCA é fonte de autorização.
     */
    @Test
    void collaboratorGrantWithExecutiveLabelSeesOnlyOwnUsage() {
        User admin = userRepository.findByTenantIdAndEmail(tenant.id(), ADMIN_EMAIL).orElseThrow();
        // Rótulo EXECUTIVE deliberadamente divergente da concessão COLLABORATOR.
        User impostor = testTenants.ensureMember(tenant, admin.id(), IMPOSTOR_EMAIL, "User",
            PASSWORD, HierarchyLevel.EXECUTIVE, marketingDepartment, "COLLABORATOR");
        usage(admin, marketingDepartment, 1000, 2000);
        usage(admin, salesDepartment, 500, 500);
        usage(impostor, marketingDepartment, 10, 20);

        Map<?, ?> usageMetrics = getJson(login(IMPOSTOR_EMAIL), "/api/v1/observability/metrics/usage");

        // SELF: apenas o próprio uso — 1 usuário ativo e 1 requisição no total.
        assertThat(usageMetrics.get("activeUsers")).isEqualTo(1);
        long total = ((List<Map<String, Object>>) usageMetrics.get("series")).stream()
            .mapToLong(point -> ((Number) point.get("requests")).longValue())
            .sum();
        assertThat(total).isEqualTo(1);
    }

    @Test
    void shouldRejectInconsistentPeriod() {
        String token = login(ADMIN_EMAIL);

        assertThat(status(token,
            "/api/v1/observability/metrics/usage?from=2026-02-01T00:00:00Z&to=2026-01-01T00:00:00Z"))
            .isEqualTo(400);
    }

    @Test
    void shouldRejectMetricsWithoutAuthentication() {
        try {
            client.get().uri("/api/v1/observability/metrics/usage").retrieve().toBodilessEntity();
            throw new AssertionError("Esperado 401");
        } catch (RestClientResponseException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(401);
        }
    }
}
