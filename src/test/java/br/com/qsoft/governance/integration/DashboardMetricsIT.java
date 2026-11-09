package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.aigateway.application.CreateModel;
import br.com.qsoft.governance.aigateway.application.ChangeModelStatus;
import br.com.qsoft.governance.aigateway.application.ChangeModelStatusCommand;
import br.com.qsoft.governance.aigateway.application.CreateModelCommand;
import br.com.qsoft.governance.aigateway.application.CreateProvider;
import br.com.qsoft.governance.aigateway.application.CreateProviderCommand;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.aigateway.domain.model.ModelStatus;
import br.com.qsoft.governance.aigateway.domain.provider.ProviderKind;
import br.com.qsoft.governance.sharedkernel.event.AIRequestCompleted;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
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
    private static final String EXEC_EMAIL = "executive@dashdemo.com";
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
    ChangeModelStatus changeModelStatus;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    TestTenants testTenants;

    private RestClient client;
    private Tenant tenant;
    private UUID marketingDepartment;
    private UUID salesDepartment;
    private Model model;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        tenant = ensureTenant();
        var bootstrap = testTenants.admin(tenant, ADMIN_EMAIL);
        testTenants.ensureMember(tenant, bootstrap.id(), EXEC_EMAIL, "Executive", PASSWORD,
            HierarchyLevel.EXECUTIVE, null, "EXECUTIVE");
        marketingDepartment = UUID.randomUUID();
        salesDepartment = UUID.randomUUID();

        UUID providerId = createProvider.create(new CreateProviderCommand(
            tenant.id(), "OpenAI " + UUID.randomUUID(), ProviderKind.STUB, null, null, null)).id();
        model = createModel.create(new CreateModelCommand(
            tenant.id(), providerId, "gpt-4 " + UUID.randomUUID(), null,
            new BigDecimal("0.010000"), new BigDecimal("0.030000")));
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
        usageFor(tenant, user, departmentId, input, output);
    }

    private void usageFor(Tenant targetTenant, User user, UUID departmentId,
                          long input, long output) {
        eventPublisher.publishEvent(new AIRequestCompleted(
            UUID.randomUUID(), targetTenant.id().value(), departmentId, user.id().value(),
            model.id(), model.providerId(), input, output,
            model.estimatedCost(input, output), 100, "COMPLETED"));
    }

    private List<Map<String, Object>> costs(String token, String query) {
        return client.get().uri("/api/v1/observability/metrics/costs" + query)
            .header("Authorization", "Bearer " + token)
            .retrieve().body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
            });
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

    private int statusOf(String method, String uri, String token, Object body) {
        RestClient.RequestBodySpec spec = client.method(HttpMethod.valueOf(method)).uri(uri);
        if (token != null) {
            spec = spec.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        try {
            return spec.retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException e) {
            return e.getStatusCode().value();
        }
    }

    @Test
    void personalDashboardNeverReturnsColleaguesEvenForExecutive() {
        User admin = userRepository.findByTenantIdAndEmail(tenant.id(), EXEC_EMAIL).orElseThrow();
        User colleague = testTenants.ensureMember(tenant, admin.id(), "personal-" + UUID.randomUUID() + "@dashdemo.com",
            "Colleague", PASSWORD, HierarchyLevel.COLLABORATOR, marketingDepartment, "COLLABORATOR");
        usage(admin, marketingDepartment, 1000, 500);
        usage(colleague, marketingDepartment, 9000000, 8000000);
        String uri = "/api/v1/observability/metrics/me/";
        String injectedFilter = "?modelId=" + model.id() + "&userId=" + colleague.id().value() + "&tenantId=" + UUID.randomUUID();
        Map<?, ?> personal = getJson(login(EXEC_EMAIL), uri + "usage" + injectedFilter);
        assertThat(personal.get("activeUsers")).isEqualTo(1);
        var rows = (List<?>) personal.get("series");
        long requests = rows.stream().map(row -> (Map<?, ?>) row)
            .mapToLong(row -> ((Number) row.get("requests")).longValue()).sum();
        assertThat(requests).isEqualTo(1);
        // SPI-93: os novos campos temporais devem manter o mesmo recorte pessoal,
        // mesmo quando o cliente injeta IDs de colega e de outro tenant.
        assertThat(rows).singleElement().satisfies(value -> {
            var row = (Map<?, ?>) value;
            assertThat(((Number) row.get("inputTokens")).longValue()).isEqualTo(1000);
            assertThat(((Number) row.get("outputTokens")).longValue()).isEqualTo(500);
            assertThat(row.get("estimatedCost")).isNotNull();
        });
        var costs = client.get().uri(uri + "costs" + injectedFilter)
            .header("Authorization", "Bearer " + login(EXEC_EMAIL))
            .retrieve().body(new ParameterizedTypeReference<List<Map<String, Object>>>() { });
        assertThat(costs).singleElement().satisfies(row -> {
            assertThat(((Number) row.get("tokensEntrada")).longValue()).isEqualTo(1000);
            assertThat(((Number) row.get("tokensSaida")).longValue()).isEqualTo(500);
        });
        assertThat(status(login(colleague.email()), uri + "usage" + injectedFilter)).isEqualTo(200);
        assertThat(status("invalid.token.signature", uri + "usage")).isEqualTo(401);
    }

    @Test
    void historicalDashboardEnforcesAuthenticationAndEffectiveScope() {
        String path = "/api/v1/observability/metrics/historical";
        assertThat(statusOf("GET", path, null, null)).isEqualTo(401);
        String adminToken = login(EXEC_EMAIL);
        assertThat(getJson(adminToken, path).get("sourceKind")).isEqualTo("HISTORICAL");
        assertThat(status(adminToken, path + "?from=2026-08-01&to=2026-07-31")).isEqualTo(400);
        assertThat(status(adminToken, path + "?from=2026-02-30")).isEqualTo(400);
        User admin = userRepository.findByTenantIdAndEmail(tenant.id(), ADMIN_EMAIL).orElseThrow();
        String member = "historical-" + UUID.randomUUID() + "@dashdemo.com";
        testTenants.ensureMember(tenant, admin.id(), member, "Restricted", PASSWORD,
            HierarchyLevel.MANAGER, marketingDepartment, "MANAGER");
        assertThat(status(login(member), path)).isEqualTo(403);
    }

    @Test
    void shouldAggregateUsageAndCostByPeriodAndDepartment() {
        User admin = userRepository.findByTenantIdAndEmail(tenant.id(), ADMIN_EMAIL).orElseThrow();
        usage(admin, marketingDepartment, 1000, 2000);
        usage(admin, salesDepartment, 500, 500);

        String token = login(EXEC_EMAIL);
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
            assertThat(cost.get("chave")).isEqualTo(marketingDepartment.toString()));
    }

    @Test
    void shouldFilterCostsByDepartment() {
        User admin = userRepository.findByTenantIdAndEmail(tenant.id(), ADMIN_EMAIL).orElseThrow();
        usage(admin, marketingDepartment, 1000, 2000);
        usage(admin, salesDepartment, 500, 500);

        List<Map<String, Object>> costs = client.get()
            .uri("/api/v1/observability/metrics/costs?departmentId=" + marketingDepartment)
            .header("Authorization", "Bearer " + login(EXEC_EMAIL))
            .retrieve().body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
            });

        assertThat(costs).isNotEmpty();
        assertThat(costs).allSatisfy(cost ->
            assertThat(cost.get("chave")).isEqualTo(marketingDepartment.toString()));
    }

    @Test
    void managerCannotRequestAnotherDepartmentFilter() {
        User admin = userRepository.findByTenantIdAndEmail(tenant.id(), ADMIN_EMAIL).orElseThrow();
        testTenants.ensureMember(tenant, admin.id(), MANAGER_EMAIL, "User", PASSWORD,
            HierarchyLevel.MANAGER, marketingDepartment, "MANAGER");
        usage(admin, marketingDepartment, 1000, 2000);
        usage(admin, salesDepartment, 500, 500);

        // DB01: filtro fora do escopo → 403 (decisão 10).
        assertThat(status(login(MANAGER_EMAIL),
            "/api/v1/observability/metrics/costs?departmentId=" + salesDepartment))
            .isEqualTo(403);

        // Sem filtro → só o departamento da concessão (nunca o tenant).
        List<Map<String, Object>> costs = client.get()
            .uri("/api/v1/observability/metrics/costs")
            .header("Authorization", "Bearer " + login(MANAGER_EMAIL))
            .retrieve().body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
            });
        assertThat(costs).allSatisfy(cost ->
            assertThat(cost.get("chave")).isEqualTo(marketingDepartment.toString()));
    }

    /** DB02: gestor com concessões em A e B vê A ∪ B. */
    @Test
    void managerWithTwoDepartmentGrantsSeesTheUnion() {
        User admin = userRepository.findByTenantIdAndEmail(tenant.id(), ADMIN_EMAIL).orElseThrow();
        String email = "manager-db02@dashdemo.com";
        User manager = testTenants.ensureMember(tenant, admin.id(), email, "User", PASSWORD,
            HierarchyLevel.MANAGER, marketingDepartment, "MANAGER");
        // Segunda concessão departamental (B), via atribuição direta (admin).
        assertThat(statusOf("POST", "/api/v1/usuarios/" + manager.id().value() + "/concessoes",
            login(ADMIN_EMAIL),
            Map.of("roleKey", "MANAGER", "departmentId", salesDepartment.toString())))
            .isEqualTo(201);

        usage(admin, marketingDepartment, 1000, 2000);
        usage(admin, salesDepartment, 500, 500);

        List<Map<String, Object>> costs = client.get()
            .uri("/api/v1/observability/metrics/costs")
            .header("Authorization", "Bearer " + login(email))
            .retrieve().body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
            });

        assertThat(costs).extracting(cost -> cost.get("chave"))
            .contains(marketingDepartment.toString(), salesDepartment.toString());
    }

    /** DB03: mudar a lotação não muda o escopo da concessão. */
    @Test
    void changingDepartmentDoesNotChangeGrantScope() {
        User admin = userRepository.findByTenantIdAndEmail(tenant.id(), ADMIN_EMAIL).orElseThrow();
        String email = "manager-db03@dashdemo.com";
        User manager = testTenants.ensureMember(tenant, admin.id(), email, "User", PASSWORD,
            HierarchyLevel.MANAGER, marketingDepartment, "MANAGER");
        usage(admin, marketingDepartment, 1000, 2000);
        usage(admin, salesDepartment, 500, 500);

        // Admin move a lotação do gestor para B; a concessão continua em A.
        assertThat(statusOf("PATCH", "/api/v1/usuarios/" + manager.id().value(),
            login(ADMIN_EMAIL), Map.of("departmentId", salesDepartment.toString())))
            .isEqualTo(200);

        List<Map<String, Object>> costs = client.get()
            .uri("/api/v1/observability/metrics/costs")
            .header("Authorization", "Bearer " + login(email))
            .retrieve().body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
            });
        assertThat(costs).allSatisfy(cost ->
            assertThat(cost.get("chave")).isEqualTo(marketingDepartment.toString()));
    }

    /** DB04: rótulo de gestor sem concessão departamental → só os próprios dados. */
    @Test
    void labelWithoutDepartmentalGrantSeesOnlyOwnUsage() {
        User admin = userRepository.findByTenantIdAndEmail(tenant.id(), ADMIN_EMAIL).orElseThrow();
        String email = "leader-db04@dashdemo.com";
        // Rótulo LEADER, mas a concessão é COLLABORATOR/SELF.
        User leader = testTenants.ensureMember(tenant, admin.id(), email, "User", PASSWORD,
            HierarchyLevel.LEADER, marketingDepartment, "COLLABORATOR");
        usage(admin, marketingDepartment, 1000, 2000);
        usage(leader, marketingDepartment, 10, 20);

        Map<?, ?> usageMetrics = getJson(login(email), "/api/v1/observability/metrics/usage");

        assertThat(usageMetrics.get("activeUsers")).isEqualTo(1);
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
    void dashboardCatalogIsAvailableWithoutAdministrativePermissionAndIsTenantScoped() {
        User admin = userRepository.findByTenantIdAndEmail(tenant.id(), ADMIN_EMAIL).orElseThrow();
        String email = "catalog-" + UUID.randomUUID() + "@dashdemo.com";
        testTenants.ensureMember(tenant, admin.id(), email, "Colaborador", PASSWORD,
            HierarchyLevel.COLLABORATOR, marketingDepartment, "COLLABORATOR");
        Model disabled = createModel.create(new CreateModelCommand(tenant.id(), model.providerId(),
            "disabled-" + UUID.randomUUID(), null, BigDecimal.ONE, BigDecimal.ONE));
        changeModelStatus.change(new ChangeModelStatusCommand(tenant.id(), disabled.id(), ModelStatus.DISABLED));
        Tenant other = testTenants.ensureTenant("catalogother", "Outro", "Admin", "admin@catalogother.com", PASSWORD);
        UUID provider = createProvider.create(new CreateProviderCommand(other.id(),
            "provider-" + UUID.randomUUID(), ProviderKind.STUB, null, null, null)).id();
        Model foreign = createModel.create(new CreateModelCommand(other.id(), provider,
            "foreign-" + UUID.randomUUID(), null, BigDecimal.ONE, BigDecimal.ONE));
        String token = login(email);
        List<Map<String, Object>> catalog = client.get().uri("/api/v1/ai-gateway/models/dashboard")
            .header("Authorization", "Bearer " + token).retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() { });
        assertThat(catalog).anyMatch(row -> row.get("id").equals(model.id().toString()));
        assertThat(catalog).noneMatch(row -> row.get("id").equals(disabled.id().toString())
            || row.get("id").equals(foreign.id().toString()));
        assertThat(catalog).allSatisfy(row -> assertThat(row.keySet()).containsExactlyInAnyOrder("id", "name"));
        assertThat(statusOf("GET", "/api/v1/ai-gateway/models/dashboard", null, null)).isEqualTo(401);
    }

    @Test
    void shouldRejectInconsistentPeriod() {
        String token = login(EXEC_EMAIL);

        assertThat(status(token,
            "/api/v1/observability/metrics/usage?from=2026-02-01T00:00:00Z&to=2026-01-01T00:00:00Z"))
            .isEqualTo(400);
        assertThat(status(token,
            "/api/v1/observability/metrics/usage?from=2026-02-01&to=2026-01-01"))
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

    /** SPI-97/98: agrupamento por modelo e por usuário sobre o mesmo recorte. */
    @Test
    void shouldGroupCostsByModelAndUser() {
        User admin = userRepository.findByTenantIdAndEmail(tenant.id(), ADMIN_EMAIL).orElseThrow();
        usage(admin, marketingDepartment, 1000, 2000);

        String token = login(EXEC_EMAIL);

        List<Map<String, Object>> byModel = costs(token,
            "?groupBy=MODEL&departmentId=" + marketingDepartment);
        assertThat(byModel).hasSize(1);
        assertThat(byModel.get(0).get("chave")).isEqualTo(model.id().toString());
        assertThat(groupRequests(byModel.get(0))).isEqualTo(1);
        assertThat(groupTokens(byModel.get(0), "tokensEntrada")).isEqualTo(1000);
        assertThat(groupTokens(byModel.get(0), "tokensSaida")).isEqualTo(2000);

        List<Map<String, Object>> byUser = costs(token,
            "?groupBy=USER&departmentId=" + marketingDepartment);
        assertThat(byUser).hasSize(1);
        assertThat(byUser.get(0).get("chave")).isEqualTo(admin.id().value().toString());
        assertThat(groupRequests(byUser.get(0))).isEqualTo(1);
    }

    /** SPI-99/100: filtros combinados de período, departamento, modelo e usuário. */
    @Test
    void shouldCombineDepartmentModelAndUserFilters() {
        User admin = userRepository.findByTenantIdAndEmail(tenant.id(), ADMIN_EMAIL).orElseThrow();
        User collaborator = testTenants.ensureMember(tenant, admin.id(),
            "combo-" + UUID.randomUUID() + "@dashdemo.com", "Combo", PASSWORD,
            HierarchyLevel.COLLABORATOR, marketingDepartment, "COLLABORATOR");
        usage(admin, marketingDepartment, 1000, 2000);
        usage(collaborator, marketingDepartment, 500, 500);

        List<Map<String, Object>> filtered = costs(login(EXEC_EMAIL),
            "?groupBy=USER&departmentId=" + marketingDepartment
                + "&modelId=" + model.id()
                + "&userId=" + admin.id().value());

        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).get("chave")).isEqualTo(admin.id().value().toString());
        assertThat(groupRequests(filtered.get(0))).isEqualTo(1);
    }

    /** MANAGER(A) agrupando por usuário só vê usuários com uso em A (SPI-113). */
    @Test
    void managerGroupingByUserSeesOnlyUsersFromItsDepartment() {
        User admin = userRepository.findByTenantIdAndEmail(tenant.id(), ADMIN_EMAIL).orElseThrow();
        String managerEmail = "group-manager-" + UUID.randomUUID() + "@dashdemo.com";
        User manager = testTenants.ensureMember(tenant, admin.id(), managerEmail, "Manager",
            PASSWORD, HierarchyLevel.MANAGER, marketingDepartment, "MANAGER");
        User other = testTenants.ensureMember(tenant, admin.id(),
            "group-other-" + UUID.randomUUID() + "@dashdemo.com", "Other", PASSWORD,
            HierarchyLevel.COLLABORATOR, salesDepartment, "COLLABORATOR");
        usage(admin, marketingDepartment, 1000, 2000);
        usage(other, salesDepartment, 500, 500);

        String managerToken = login(managerEmail);
        List<Map<String, Object>> byUser = costs(managerToken, "?groupBy=USER");

        assertThat(byUser).noneMatch(row -> other.id().value().toString().equals(row.get("chave")));
        assertThat(byUser).anyMatch(row -> admin.id().value().toString().equals(row.get("chave")));
        assertThat(status(managerToken,
            "/api/v1/observability/metrics/costs?userId=" + other.id().value())).isEqualTo(403);
        assertThat(status(managerToken,
            "/api/v1/observability/metrics/usage?userId=" + other.id().value())).isEqualTo(403);
        assertThat(manager.id().value()).isNotNull();
    }

    /** SELF: agrupar por usuário devolve apenas o próprio usuário. */
    @Test
    void selfOnlyGroupingByUserReturnsOnlyTheActor() {
        User admin = userRepository.findByTenantIdAndEmail(tenant.id(), ADMIN_EMAIL).orElseThrow();
        String email = "self-" + UUID.randomUUID() + "@dashdemo.com";
        User collaborator = testTenants.ensureMember(tenant, admin.id(), email, "User",
            PASSWORD, HierarchyLevel.COLLABORATOR, marketingDepartment, "COLLABORATOR");
        usage(admin, marketingDepartment, 1000, 2000);
        usage(collaborator, marketingDepartment, 10, 20);

        List<Map<String, Object>> byUser = costs(login(email), "?groupBy=USER");

        assertThat(byUser).extracting(row -> row.get("chave"))
            .containsExactly(collaborator.id().value().toString());
    }

    /** O custo nunca cruza tenants, mesmo com IDs de outra organização. */
    @Test
    void shouldIsolateCostsFromAnotherTenant() {
        Tenant otherTenant = testTenants.ensureTenant("dashother", "Dash Other", "Admin",
            "admin@dashother.com", PASSWORD);
        User otherAdmin = testTenants.admin(otherTenant, "admin@dashother.com");
        usageFor(otherTenant, otherAdmin, UUID.randomUUID(), 100, 100);

        String adminToken = login(EXEC_EMAIL);
        List<Map<String, Object>> byUser = costs(adminToken, "?groupBy=USER");

        assertThat(byUser).noneMatch(row ->
            otherAdmin.id().value().toString().equals(row.get("chave")));
        assertThat(status(adminToken,
            "/api/v1/observability/metrics/costs?userId=" + otherAdmin.id().value()))
            .isEqualTo(403);
    }

    private static long groupRequests(Map<String, Object> group) {
        return ((Number) group.get("requisicoes")).longValue();
    }

    private static long groupTokens(Map<String, Object> group, String field) {
        return ((Number) group.get(field)).longValue();
    }
}
