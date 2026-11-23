package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.GovernanceApplication;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.support.TestTenants;
import java.util.HashMap;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * API do catálogo global + overrides (ADR-44): leitura com `AGENT:READ`,
 * administração com `AGENT:MANAGE` no tenant, 401/403/404/409 e isolação
 * Tenant A ≠ Tenant B.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = GovernanceApplication.class)
class AgentsCatalogIT {

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

    private static final String SLUG = "agentscatalog";
    private static final String OTHER_SLUG = "agentscatalogb";
    private static final String ADMIN_EMAIL = "admin@agentscatalog.com";
    private static final String COLLAB_EMAIL = "collab@agentscatalog.com";
    private static final String LEADER_EMAIL = "leader@agentscatalog.com";
    private static final String OTHER_ADMIN_EMAIL = "admin@agentscatalogb.com";
    private static final String PASSWORD = "secret123";
    private static final UUID DEPARTMENT = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String CATALOG = "/api/v1/agentes/catalogo";

    @LocalServerPort
    int port;

    @Autowired
    TestTenants testTenants;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        Tenant tenant = testTenants.ensureTenant(SLUG, "Agents Catalog", "Admin",
            ADMIN_EMAIL, PASSWORD);
        var adminId = testTenants.admin(tenant, ADMIN_EMAIL).id();
        testTenants.ensureMember(tenant, adminId, COLLAB_EMAIL, "Collab", PASSWORD,
            HierarchyLevel.COLLABORATOR, null, "COLLABORATOR");
        testTenants.ensureMember(tenant, adminId, LEADER_EMAIL, "Leader", PASSWORD,
            HierarchyLevel.LEADER, DEPARTMENT, "LEADER");
        testTenants.ensureTenant(OTHER_SLUG, "Agents Catalog B", "Admin B",
            OTHER_ADMIN_EMAIL, PASSWORD);
        jdbcTemplate.update("DELETE FROM agents_catalog_overrides");
    }

    @Test
    void collaboratorListsSeventeenAgentsWithoutPromptContent() {
        Map<String, Object> body = getCatalog(login(COLLAB_EMAIL));

        List<Map<String, Object>> agents = agents(body);
        assertThat(agents).hasSize(17);
        assertThat(agents).extracting(agent -> agent.get("agentKey"))
            .doesNotContain("merge", "task", "result");
        assertThat(agents).allSatisfy(agent ->
            assertThat(agent).doesNotContainKeys("promptTemplate", "toolsJson", "knowledgeScope",
                "modelPolicy"));

        Map<String, Object> code = agent(body, "code");
        assertThat(code.get("status")).isEqualTo("DRAFT");
        assertThat(code.get("executable")).isEqualTo(false);
        assertThat(code.get("overridden")).isEqualTo(false);

        assertThat(agent(body, "policies").get("executable")).isEqualTo(true);
        assertThat(agent(body, "writing").get("executable")).isEqualTo(true);
        assertThat(agent(body, "data").get("executable")).isEqualTo(true);
        assertThat(agent(body, "projects").get("executable")).isEqualTo(true);
        assertThat(agent(body, "research").get("executable")).isEqualTo(true);
        assertThat(agent(body, "review").get("executable")).isEqualTo(true);
        assertThat(agent(body, "finance").get("executable")).isEqualTo(false);
    }

    @Test
    void catalogRequiresAuthentication() {
        int status = getStatus(null, CATALOG);

        assertThat(status).isEqualTo(401);
    }

    @Test
    void collaboratorCannotChangeCatalogOverride() {
        int status = putOverride(login(COLLAB_EMAIL), "code", overrideBody(1, "ACTIVE", null));

        assertThat(status).isEqualTo(403);
    }

    @Test
    void leaderCannotChangeTenantCatalogOverride() {
        // AGENT:MANAGE de LEADER é DEPARTMENT; o catálogo exige escopo TENANT.
        int status = putOverride(login(LEADER_EMAIL), "code", overrideBody(1, "ACTIVE", null));

        assertThat(status).isEqualTo(403);
    }

    @Test
    void tenantAdminOverridesCatalogAndOtherTenantIsUnaffected() {
        ResponseEntity<Map<String, Object>> response = putOverrideResponse(
            login(ADMIN_EMAIL), "code", overrideBody(1, "ACTIVE", null));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("agentKey", "code");
        assertThat(response.getBody()).containsEntry("executable", true);
        assertThat(response.getBody()).containsEntry("overridden", true);
        assertThat(response.getBody()).containsKey("overrideUpdatedAt");

        Map<String, Object> tenantA = getCatalog(login(COLLAB_EMAIL));
        assertThat(agent(tenantA, "code").get("status")).isEqualTo("ACTIVE");
        assertThat(agent(tenantA, "code").get("executable")).isEqualTo(true);
        assertThat(agent(tenantA, "code").get("overridden")).isEqualTo(true);

        Map<String, Object> tenantB = getCatalog(login(OTHER_ADMIN_EMAIL));
        assertThat(agent(tenantB, "code").get("status")).isEqualTo("DRAFT");
        assertThat(agent(tenantB, "code").get("executable")).isEqualTo(false);
        assertThat(agent(tenantB, "code").get("overridden")).isEqualTo(false);
    }

    @Test
    void unknownAgentKeyOrVersionReturns404() {
        String token = login(ADMIN_EMAIL);

        assertThat(putOverride(token, "merge", overrideBody(1, "ACTIVE", null))).isEqualTo(404);
        assertThat(putOverride(token, "policies", overrideBody(2, "ACTIVE", null))).isEqualTo(404);
        assertThat(deleteOverride(token, "merge")).isEqualTo(404);
    }

    @Test
    void staleExpectedTimestampReturns409() {
        String token = login(ADMIN_EMAIL);
        assertThat(putOverride(token, "code", overrideBody(1, "ACTIVE", null))).isEqualTo(200);

        int stale = putOverride(token, "code",
            overrideBody(1, "ARCHIVED", "1970-01-01T00:00:00Z"));

        assertThat(stale).isEqualTo(409);
    }

    @Test
    void emptyOverrideIsRejectedAsBadRequest() {
        int status = putOverride(login(ADMIN_EMAIL), "code", Map.of("version", 1));

        assertThat(status).isEqualTo(400);
    }

    @Test
    void knowledgeScopeOutsideTheClosedVocabularyIsRejected() {
        Map<String, Object> body = overrideBody(1, "ACTIVE", null);
        body.put("knowledgeScopeJson", "{\"mode\":\"TENANT\",\"topics\":[\"políticas\"]}");

        assertThat(putOverride(login(ADMIN_EMAIL), "code", body)).isEqualTo(400);
    }

    @Test
    void deleteRestoresGlobalAndIsIdempotent() {
        String token = login(ADMIN_EMAIL);
        putOverride(token, "code", overrideBody(1, "ACTIVE", null));

        assertThat(deleteOverride(token, "code")).isEqualTo(204);
        assertThat(agent(getCatalog(login(COLLAB_EMAIL)), "code").get("status")).isEqualTo("DRAFT");
        assertThat(deleteOverride(token, "code")).isEqualTo(204);
    }

    @Test
    void globalCatalogIsNotEditableAtRuntime() {
        String token = login(ADMIN_EMAIL);

        assertThat(postStatus(token, CATALOG)).isEqualTo(405);
        assertThat(putStatus(token, CATALOG, Map.of("name", "x"))).isEqualTo(405);
    }

    private Map<String, Object> getCatalog(String token) {
        ResponseEntity<Map<String, Object>> response = client.get().uri(CATALOG)
            .headers(headers -> {
                if (token != null) {
                    headers.setBearerAuth(token);
                }
            })
            .retrieve()
            .toEntity(new ParameterizedTypeReference<>() {
            });
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private ResponseEntity<Map<String, Object>> putOverrideResponse(String token, String agentKey,
                                                                    Map<String, Object> body) {
        return client.put().uri(CATALOG + "/{agentKey}/override", agentKey)
            .contentType(MediaType.APPLICATION_JSON)
            .headers(headers -> headers.setBearerAuth(token))
            .body(body)
            .retrieve()
            .toEntity(new ParameterizedTypeReference<>() {
            });
    }

    private int putOverride(String token, String agentKey, Map<String, Object> body) {
        try {
            return putOverrideResponse(token, agentKey, body).getStatusCode().value();
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value();
        }
    }

    private int putStatus(String token, String path, Map<String, Object> body) {
        try {
            return client.put().uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(token))
                .body(body)
                .retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value();
        }
    }

    private int postStatus(String token, String path) {
        try {
            return client.post().uri(path)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value();
        }
    }

    private int deleteOverride(String token, String agentKey) {
        try {
            return client.delete().uri(CATALOG + "/{agentKey}/override", agentKey)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value();
        }
    }

    private int getStatus(String token, String path) {
        try {
            return client.get().uri(path)
                .headers(headers -> {
                    if (token != null) {
                        headers.setBearerAuth(token);
                    }
                })
                .retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value();
        }
    }

    private static Map<String, Object> overrideBody(int version, String status,
                                                    String expectedUpdatedAt) {
        Map<String, Object> body = new HashMap<>();
        body.put("version", version);
        body.put("status", status);
        body.put("promptTemplate", "Prompt revisado pelo admin do tenant.");
        body.put("toolsJson", "[]");
        body.put("knowledgeScopeJson", "{\"mode\":\"NONE\"}");
        body.put("modelPolicyJson", "{\"mode\":\"INHERIT\"}");
        if (expectedUpdatedAt != null) {
            body.put("expectedUpdatedAt", expectedUpdatedAt);
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> agents(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("agentes");
    }

    private static Map<String, Object> agent(Map<String, Object> body, String agentKey) {
        return agents(body).stream()
            .filter(agent -> agentKey.equals(agent.get("agentKey")))
            .findFirst()
            .orElseThrow();
    }

    private String login(String email) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", PASSWORD))
            .retrieve().body(Map.class);
        return response == null ? null : (String) response.get("token");
    }
}
