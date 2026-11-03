package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.GovernanceApplication;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.support.TestTenants;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
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
 * Catálogo administrativo (SPI-87): PR06 (GET da política, outro tenant → 404,
 * sem política → vazio) e PR07 (preço novo não altera tentativa antiga).
 * Exige Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = GovernanceApplication.class)
class CatalogAdminIT {

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
    private String adminAEmail;
    private String adminBEmail;
    private UUID departmentA;
    private UUID departmentOtherTenant;
    private UUID providerId;
    private UUID modelId;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        String slug = "catalog" + UUID.randomUUID().toString().substring(0, 8).toLowerCase();
        adminAEmail = "admin@" + slug + ".com";
        adminBEmail = "admin@" + slug + "b.com";
        tenantA = testTenants.ensureTenant(slug, "Catalog A", "Admin", adminAEmail, PASSWORD);
        tenantB = testTenants.ensureTenant(slug + "b", "Catalog B", "Admin", adminBEmail, PASSWORD);
        adminA = testTenants.admin(tenantA, adminAEmail);

        String tokenA = login(adminAEmail);
        departmentA = createDepartment(tokenA, "Marketing " + UUID.randomUUID());
        departmentOtherTenant = createDepartment(login(adminBEmail),
            "Outro " + UUID.randomUUID());
        providerId = createProvider(tokenA);
        modelId = createModel(tokenA, "gpt-4o-" + UUID.randomUUID(), "gpt-4o-2024",
            "0.005", "0.015");
    }

    @Test
    void pr06_lerPoliticaVaziaEDepartamentoDeOutroTenantRetorna404() {
        String token = login(adminAEmail);

        Map<String, Object> empty = get("/api/v1/ai-gateway/departments/"
            + departmentA + "/model-policy", token);
        assertThat(empty.get("departmentId")).isEqualTo(departmentA.toString());
        assertThat(empty.get("defaultModelId")).isNull();
        assertThat((Iterable<?>) empty.get("allowedModelIds")).isEmpty();

        assertThat(status("GET", "/api/v1/ai-gateway/departments/"
            + departmentOtherTenant + "/model-policy", token, null)).isEqualTo(404);

        assertThat(status("PUT", "/api/v1/ai-gateway/departments/"
            + departmentA + "/model-policy", token,
            Map.of("allowedModelIds", new UUID[] { modelId },
                "defaultModelId", modelId.toString()))).isEqualTo(204);

        Map<String, Object> configured = get("/api/v1/ai-gateway/departments/"
            + departmentA + "/model-policy", token);
        assertThat(configured.get("defaultModelId")).isEqualTo(modelId.toString());
        assertThat(((java.util.List<?>) configured.get("allowedModelIds")).stream()
            .map(Object::toString).toList()).containsExactly(modelId.toString());
    }

    @Test
    void pr07_precoNovoNaoAlteraTentativaAntiga() {
        UUID requestId = UUID.randomUUID();
        insertAttempt(requestId);
        Map<String, Object> before = jdbcTemplate.queryForMap("""
            SELECT estimated_cost, unit_price_input, unit_price_output
            FROM aigateway_request_attempts WHERE request_id = ?
            """, requestId);
        assertThat(decimal(before.get("estimated_cost"))).isEqualByComparingTo("0.000700");

        String token = login(adminAEmail);
        Map<String, Object> updated = put("/api/v1/ai-gateway/models/" + modelId, token, Map.of(
            "name", "gpt-4o-editado",
            "externalName", "gpt-4o-2024-v2",
            "inputPricePer1k", "0.010",
            "outputPricePer1k", "0.020"));
        assertThat(decimal(updated.get("inputPricePer1k"))).isEqualByComparingTo("0.010");
        assertThat(decimal(updated.get("outputPricePer1k"))).isEqualByComparingTo("0.020");
        assertThat(updated.get("status")).isEqualTo("AVAILABLE");

        Map<String, Object> after = jdbcTemplate.queryForMap("""
            SELECT estimated_cost, unit_price_input, unit_price_output
            FROM aigateway_request_attempts WHERE request_id = ?
            """, requestId);
        assertThat(decimal(after.get("estimated_cost"))).isEqualByComparingTo("0.000700");
        assertThat(decimal(after.get("unit_price_input"))).isEqualByComparingTo("0.005");
        assertThat(decimal(after.get("unit_price_output"))).isEqualByComparingTo("0.015");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String login(String email) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", PASSWORD))
            .retrieve().body(Map.class);
        return response == null ? null : (String) response.get("token");
    }

    private UUID createDepartment(String token, String name) {
        Map<String, Object> response = post("/api/v1/departamentos", token, Map.of("name", name));
        return UUID.fromString((String) response.get("id"));
    }

    private UUID createProvider(String token) {
        Map<String, Object> response = post("/api/v1/ai-gateway/providers", token,
            Map.of("name", "Provider " + UUID.randomUUID(), "kind", "STUB"));
        return UUID.fromString((String) response.get("id"));
    }

    private UUID createModel(String token, String name, String externalName,
                             String inputPrice, String outputPrice) {
        Map<String, Object> response = post("/api/v1/ai-gateway/models", token, Map.of(
            "providerId", providerId.toString(), "name", name, "externalName", externalName,
            "inputPricePer1k", inputPrice, "outputPricePer1k", outputPrice));
        return UUID.fromString((String) response.get("id"));
    }

    private Map<String, Object> post(String uri, String token, Object body) {
        return client.post().uri(uri)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve().body(new ParameterizedTypeReference<>() {
            });
    }

    private Map<String, Object> get(String uri, String token) {
        return client.get().uri(uri)
            .header("Authorization", "Bearer " + token)
            .retrieve().body(new ParameterizedTypeReference<>() {
            });
    }

    private Map<String, Object> put(String uri, String token, Object body) {
        return client.put().uri(uri)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve().body(new ParameterizedTypeReference<>() {
            });
    }

    private int status(String method, String uri, String token, Object body) {
        RestClient.RequestBodySpec spec = client.method(org.springframework.http.HttpMethod
            .valueOf(method)).uri(uri);
        if (token != null) spec = spec.header("Authorization", "Bearer " + token);
        if (body != null) spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        try {
            return spec.retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value();
        }
    }

    private static BigDecimal decimal(Object value) {
        return new BigDecimal(value.toString());
    }

    private void insertAttempt(UUID requestId) {
        jdbcTemplate.update("""
            INSERT INTO aigateway_request_attempts
                (request_id, tenant_id, user_id, department_id, purpose, model_id, provider_id,
                 authorization_status, execution_status, delivery_status,
                 input_tokens, output_tokens, cost_status, estimated_cost,
                 unit_price_input, unit_price_output, latency_ms, delivery_attempts,
                 created_at, updated_at)
            VALUES (?, ?, ?, ?, 'CHAT', ?, ?, 'GRANTED', 'SUCCEEDED', 'DELIVERED',
                    100, 200, 'KNOWN', ?, ?, ?, 100, 0, ?, ?)
            """,
            requestId, tenantA.id().value(), adminA.id().value(), departmentA,
            modelId, providerId, new BigDecimal("0.000700"),
            new BigDecimal("0.005"), new BigDecimal("0.015"),
            Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
    }
}
