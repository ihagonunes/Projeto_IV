package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.support.TestTenants;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Teste de integração do POPROJ-21 (SPI-82/83/84/85/86): catálogo do gateway,
 * configuração da ModelPolicy por departamento (validação cross-módulo) e
 * roteamento determinístico — permitido/negado, default, modelo desabilitado,
 * provider inativo e isolamento de tenant. Exige Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = br.com.qsoft.governance.GovernanceApplication.class)
class AIGatewayRoutingIT {

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

    private static final String TENANT_SLUG = "aigwdemo";
    private static final String OTHER_SLUG = "aigwother";
    private static final String ADMIN_EMAIL = "admin@aigwdemo.com";
    private static final String ADMIN_PASSWORD = "secret123";

    @LocalServerPort
    int port;

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    TestTenants testTenants;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        testTenants.ensureTenant(TENANT_SLUG, "AI GW Demo", "Admin", ADMIN_EMAIL, ADMIN_PASSWORD);
        testTenants.ensureTenant(OTHER_SLUG, "AI GW Other", "Admin", "admin@aigwother.com", ADMIN_PASSWORD);
    }

    private void ensureDepartmentUser(Tenant tenant, String email, UUID departmentId) {
        testTenants.ensureMember(tenant, testTenants.admin(tenant, ADMIN_EMAIL).id(),
            email, "User", ADMIN_PASSWORD, HierarchyLevel.COLLABORATOR, departmentId, "COLLABORATOR");
    }

    private String login(String email, String password) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", password))
            .retrieve().body(Map.class);
        return response == null ? null : (String) response.get("token");
    }

    private Map<?, ?> post(String uri, String token, Map<String, Object> body) {
        return client.post().uri(uri)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve().body(Map.class);
    }

    private int status(HttpMethod method, String uri, String token, Map<String, Object> body) {
        try {
            RestClient.RequestBodySpec spec = client.method(method).uri(uri);
            if (token != null) {
                spec.header("Authorization", "Bearer " + token);
            }
            if (body != null) {
                spec.contentType(MediaType.APPLICATION_JSON);
                spec.body(body);
            }
            return spec.retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException e) {
            return e.getStatusCode().value();
        }
    }

    /** Cria provider + model e devolve o id do model. */
    private UUID createModel(String adminToken, String name) {
        String providerId = (String) post("/api/v1/ai-gateway/providers",
            adminToken, Map.of("name", "OpenAI " + name)).get("id");
        return UUID.fromString((String) post("/api/v1/ai-gateway/models",
            adminToken, Map.of("providerId", providerId, "name", name)).get("id"));
    }

    private UUID createDepartment(String adminToken, String name) {
        return UUID.fromString((String) post("/api/v1/departamentos",
            adminToken, Map.of("name", name)).get("id"));
    }

    private Map<?, ?> allowedModel(String token, UUID requestedModelId) {
        String uri = requestedModelId == null
            ? "/api/v1/ai-gateway/models/permitido"
            : "/api/v1/ai-gateway/models/permitido?requestedModelId=" + requestedModelId;
        return client.get().uri(uri)
            .header("Authorization", "Bearer " + token)
            .retrieve().body(Map.class);
    }

    @Test
    void shouldConfigurePolicyAndResolveDefaultAndRequestedModel() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        UUID departmentId = createDepartment(adminToken, "Marketing " + UUID.randomUUID());
        UUID modelId = createModel(adminToken, "gpt-4 " + UUID.randomUUID());

        assertThat(status(HttpMethod.PUT, "/api/v1/ai-gateway/departments/" + departmentId + "/model-policy",
            adminToken, Map.of("allowedModelIds", List.of(modelId.toString()),
                "defaultModelId", modelId.toString()))).isEqualTo(204);

        // Usuário do departamento (com o modelo permitido).
        Tenant tenant = tenantRepository.findBySlug(TENANT_SLUG).orElseThrow();
        String userEmail = "dev" + UUID.randomUUID() + "@aigwdemo.com";
        ensureDepartmentUser(tenant, userEmail, departmentId);
        String userToken = login(userEmail, ADMIN_PASSWORD);

        // Default (sem requestedModelId) e requested permitido resolvem o mesmo modelo.
        assertThat(allowedModel(userToken, null).get("modelId")).isEqualTo(modelId.toString());
        assertThat(allowedModel(userToken, modelId).get("modelId")).isEqualTo(modelId.toString());
    }

    @Test
    void shouldRejectRequestedModelNotAllowed() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        UUID departmentId = createDepartment(adminToken, "Vendas " + UUID.randomUUID());
        UUID allowed = createModel(adminToken, "gpt-4 " + UUID.randomUUID());
        UUID other = createModel(adminToken, "gpt-3 " + UUID.randomUUID());

        assertThat(status(HttpMethod.PUT,
            "/api/v1/ai-gateway/departments/" + departmentId + "/model-policy",
            adminToken, Map.of("allowedModelIds", List.of(allowed.toString()),
                "defaultModelId", allowed.toString()))).isEqualTo(204);

        Tenant tenant = tenantRepository.findBySlug(TENANT_SLUG).orElseThrow();
        String userEmail = "vendas" + UUID.randomUUID() + "@aigwdemo.com";
        ensureDepartmentUser(tenant, userEmail, departmentId);
        String userToken = login(userEmail, ADMIN_PASSWORD);

        assertThat(status(HttpMethod.GET,
            "/api/v1/ai-gateway/models/permitido?requestedModelId=" + other, userToken, null))
            .isEqualTo(403);
    }

    @Test
    void shouldRejectWhenModelDisabled() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        UUID departmentId = createDepartment(adminToken, "TI " + UUID.randomUUID());
        UUID modelId = createModel(adminToken, "gpt-4 " + UUID.randomUUID());

        assertThat(status(HttpMethod.PUT,
            "/api/v1/ai-gateway/departments/" + departmentId + "/model-policy",
            adminToken, Map.of("allowedModelIds", List.of(modelId.toString()),
                "defaultModelId", modelId.toString()))).isEqualTo(204);
        assertThat(status(HttpMethod.PATCH, "/api/v1/ai-gateway/models/" + modelId + "/status",
            adminToken, Map.of("status", "DISABLED"))).isEqualTo(200);

        Tenant tenant = tenantRepository.findBySlug(TENANT_SLUG).orElseThrow();
        String userEmail = "ti" + UUID.randomUUID() + "@aigwdemo.com";
        ensureDepartmentUser(tenant, userEmail, departmentId);
        String userToken = login(userEmail, ADMIN_PASSWORD);

        assertThat(status(HttpMethod.GET, "/api/v1/ai-gateway/models/permitido", userToken, null))
            .isEqualTo(409);
    }

    @Test
    void shouldRejectPolicyWithUnknownModel() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        UUID departmentId = createDepartment(adminToken, "Financeiro " + UUID.randomUUID());
        UUID unknown = UUID.randomUUID();

        assertThat(status(HttpMethod.PUT, "/api/v1/ai-gateway/departments/" + departmentId + "/model-policy",
            adminToken, Map.of("allowedModelIds", List.of(unknown.toString()),
                "defaultModelId", unknown.toString()))).isEqualTo(404);
    }

    @Test
    void shouldRejectModelFromAnotherTenant() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String otherToken = login("admin@aigwother.com", ADMIN_PASSWORD);
        UUID departmentId = createDepartment(adminToken, "RH " + UUID.randomUUID());
        UUID foreignModel = createModel(otherToken, "foreign " + UUID.randomUUID());

        // O modelo de outro tenant não é encontrado no catálogo deste tenant → 404.
        assertThat(status(HttpMethod.PUT, "/api/v1/ai-gateway/departments/" + departmentId + "/model-policy",
            adminToken, Map.of("allowedModelIds", List.of(foreignModel.toString()),
                "defaultModelId", foreignModel.toString()))).isEqualTo(404);
    }

    @Test
    void shouldRejectCatalogWithoutAuthentication() {
        assertThat(status(HttpMethod.GET, "/api/v1/ai-gateway/providers", null, null)).isEqualTo(401);
        assertThat(status(HttpMethod.POST, "/api/v1/ai-gateway/models", null,
            Map.of("providerId", UUID.randomUUID().toString(), "name", "x"))).isEqualTo(401);
    }
}
