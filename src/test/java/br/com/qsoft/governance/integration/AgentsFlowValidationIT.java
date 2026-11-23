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
 * BE-04: validação server-side e publicação. O servidor não confia no cliente;
 * erros têm código/caminho e a versão publicada é imutável.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = GovernanceApplication.class)
class AgentsFlowValidationIT {

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

    private static final String SLUG = "agentsvalidation";
    private static final String OTHER_SLUG = "agentsvalidationb";
    private static final String ADMIN_EMAIL = "admin@agentsvalidation.com";
    private static final String MANAGER_EMAIL = "manager@agentsvalidation.com";
    private static final String COLLAB_EMAIL = "collab@agentsvalidation.com";
    private static final String OTHER_ADMIN_EMAIL = "admin@agentsvalidationb.com";
    private static final String PASSWORD = "secret123";
    private static final UUID DEPARTMENT = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final String BASE = "/api/v1/agentes/fluxos";

    @LocalServerPort
    int port;

    @Autowired
    TestTenants testTenants;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private RestClient client;
    private Tenant tenant;
    private Tenant otherTenant;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        tenant = testTenants.ensureTenant(SLUG, "Agents Validation", "Admin",
            ADMIN_EMAIL, PASSWORD);
        var adminId = testTenants.admin(tenant, ADMIN_EMAIL).id();
        testTenants.ensureMember(tenant, adminId, MANAGER_EMAIL, "Manager", PASSWORD,
            HierarchyLevel.MANAGER, DEPARTMENT, "MANAGER");
        testTenants.ensureMember(tenant, adminId, COLLAB_EMAIL, "Collab", PASSWORD,
            HierarchyLevel.COLLABORATOR, null, "COLLABORATOR");
        otherTenant = testTenants.ensureTenant(OTHER_SLUG, "Agents Validation B", "Admin B",
            OTHER_ADMIN_EMAIL, PASSWORD);

        jdbcTemplate.update("DELETE FROM agents_flow_versions");
        jdbcTemplate.update("DELETE FROM agents_flows");
    }

    @Test
    void validateAcceptsLinearGraphAndForkWithMerge() {
        String token = login(ADMIN_EMAIL);
        String id = createId(token, validGraph("policies"));

        Map<String, Object> linear = validate(token, id, validGraph("policies"));
        assertThat(linear).containsEntry("valid", true).containsEntry("needsMerge", false);
        assertThat(errors(linear)).isEmpty();

        Map<String, Object> fanOut = validate(token, id, fanOutWithMerge());
        assertThat(fanOut).containsEntry("valid", true);
    }

    @Test
    void validateReportsBranchThatNeedsMerge() {
        String token = login(ADMIN_EMAIL);
        String id = createId(token, validGraph("policies"));

        Map<String, Object> result = validate(token, id, fanOutWithoutMerge());

        assertThat(result).containsEntry("valid", false).containsEntry("needsMerge", true);
        assertThat(codes(result)).contains("BRANCH_NOT_CONSOLIDATED");
        assertThat(errors(result)).allSatisfy(error ->
            assertThat(error).containsKeys("code", "message", "path"));
    }

    @Test
    void validateReportsEveryRuleAndDoesNotPersist() {
        String token = login(ADMIN_EMAIL);
        String id = createId(token, validGraph("policies"));
        Integer before = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM agents_flow_versions WHERE flow_id = ?", Integer.class,
            UUID.fromString(id));

        Map<String, Object> result = validate(token, id, Map.of(
            "schemaVersion", 1,
            "nodes", List.of(
                Map.of("id", "t", "kind", "task", "x", 0.0, "y", 0.0),
                Map.of("id", "a", "kind", "agent", "agentKey", "merge", "x", 2.0, "y", 0.5)),
            "edges", List.of()));

        assertThat(result).containsEntry("valid", false);
        assertThat(codes(result)).contains("RESULT_COUNT_INVALID", "AGENT_KEY_UNKNOWN",
            "NODE_COORDINATES_INVALID");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM agents_flow_versions WHERE flow_id = ?", Integer.class,
            UUID.fromString(id))).isEqualTo(before);
    }

    @Test
    void validateRequiresManageAndVisibleFlow() {
        String id = createId(login(ADMIN_EMAIL), validGraph("policies"));

        assertThat(validateStatus(login(COLLAB_EMAIL), id, validGraph("policies")))
            .isEqualTo(403);
        assertThat(validateStatus(login(OTHER_ADMIN_EMAIL), id, validGraph("policies")))
            .isEqualTo(404);
        assertThat(validateStatus(login(ADMIN_EMAIL), UUID.randomUUID().toString(),
            validGraph("policies"))).isEqualTo(404);

        Map<String, Object> invalidBody = new HashMap<>();
        invalidBody.put("schemaVersion", 1);
        invalidBody.put("graphJson", List.of());
        assertThat(validateRawStatus(login(ADMIN_EMAIL), id, invalidBody)).isEqualTo(400);
    }

    @Test
    void publishRejectsVersionThatIsNotValid() {
        String token = login(ADMIN_EMAIL);
        String id = createId(token, invalidGraph());

        assertThat(publishStatus(token, id, "\"1\"")).isEqualTo(422);
    }

    @Test
    void publishPinsTheImmutableValidVersion() {
        String token = login(ADMIN_EMAIL);
        String id = createId(token, validGraph("policies"));
        assertThat(versionStatus(id, 1)).isEqualTo("VALID");

        ResponseEntity<Map<String, Object>> saved = put(token, id, "\"1\"",
            validGraph("writing"));
        assertThat(saved.getStatusCode().value()).isEqualTo(200);
        assertThat(versionStatus(id, 2)).isEqualTo("VALID");

        ResponseEntity<Map<String, Object>> published = publish(token, id, "\"2\"");

        assertThat(published.getStatusCode().value()).isEqualTo(200);
        assertThat(published.getHeaders().getETag()).isEqualTo("\"3\"");
        assertThat(published.getBody()).containsEntry("status", "PUBLISHED")
            .containsEntry("rowVersion", 3);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT graph_json::text FROM agents_flow_versions WHERE flow_id = ? AND version_number = 2",
            String.class, UUID.fromString(id))).contains("writing");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT graph_json::text FROM agents_flow_versions WHERE flow_id = ? AND version_number = 1",
            String.class, UUID.fromString(id))).contains("policies");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM agents_flow_versions WHERE flow_id = ?",
            Integer.class, UUID.fromString(id))).isEqualTo(2);
    }

    @Test
    void publishRequiresPreconditionAndRejectsSecondPublishOrEdit() {
        String token = login(ADMIN_EMAIL);
        String id = createId(token, validGraph("policies"));

        assertThat(publishStatus(token, id, null)).isEqualTo(400);
        assertThat(publishStatus(token, id, "\"9\"")).isEqualTo(412);
        assertThat(publishStatus(token, id, "\"1\"")).isEqualTo(200);
        assertThat(publishStatus(token, id, "\"2\"")).isEqualTo(409);
        assertThat(putStatus(token, id, "\"2\"", validGraph("writing"))).isEqualTo(409);
    }

    @Test
    void anotherTenantCannotValidateOrPublish() {
        String id = createId(login(ADMIN_EMAIL), validGraph("policies"));
        String other = login(OTHER_ADMIN_EMAIL);

        assertThat(validateStatus(other, id, validGraph("policies"))).isEqualTo(404);
        assertThat(publishStatus(other, id, "\"1\"")).isEqualTo(404);
    }

    @Test
    void clientValidationIsNotTrusted() {
        String token = login(ADMIN_EMAIL);
        // Cliente envia metadados "validos"; o servidor persiste INVALID e recusa publicar.
        Map<String, Object> flow = new HashMap<>();
        flow.put("name", "Auto-declarado válido");
        flow.put("schemaVersion", 1);
        flow.put("graphJson", invalidGraph());
        flow.put("valid", true);
        String id = (String) create(token, flow).getBody().get("id");

        assertThat(versionStatus(id, 1)).isEqualTo("INVALID");
        assertThat(publishStatus(token, id, "\"1\"")).isEqualTo(422);
    }

    private String createId(String token, Map<String, Object> graph) {
        return (String) create(token, Map.of("name", "Fluxo", "schemaVersion", 1,
            "graphJson", graph)).getBody().get("id");
    }

    private ResponseEntity<Map<String, Object>> create(String token, Map<String, Object> body) {
        return client.post().uri(BASE)
            .contentType(MediaType.APPLICATION_JSON)
            .headers(headers -> headers.setBearerAuth(token))
            .body(body)
            .retrieve()
            .toEntity(new ParameterizedTypeReference<>() {
            });
    }

    private Map<String, Object> validate(String token, String id, Map<String, Object> graph) {
        return client.post().uri(BASE + "/{id}/validar", id)
            .contentType(MediaType.APPLICATION_JSON)
            .headers(headers -> headers.setBearerAuth(token))
            .body(Map.of("schemaVersion", 1, "graphJson", graph))
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });
    }

    private int validateStatus(String token, String id, Map<String, Object> graph) {
        return validateRawStatus(token, id, Map.of("schemaVersion", 1, "graphJson", graph));
    }

    private int validateRawStatus(String token, String id, Map<String, Object> body) {
        try {
            client.post().uri(BASE + "/{id}/validar", id)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(token))
                .body(body)
                .retrieve().toBodilessEntity();
            return 200;
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value();
        }
    }

    private ResponseEntity<Map<String, Object>> publish(String token, String id, String ifMatch) {
        var spec = client.post().uri(BASE + "/{id}/publicar", id)
            .headers(headers -> headers.setBearerAuth(token));
        if (ifMatch != null) {
            spec = spec.headers(headers -> headers.setIfMatch(ifMatch));
        }
        return spec.retrieve().toEntity(new ParameterizedTypeReference<>() {
        });
    }

    private int publishStatus(String token, String id, String ifMatch) {
        try {
            return publish(token, id, ifMatch).getStatusCode().value();
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value();
        }
    }

    private ResponseEntity<Map<String, Object>> put(String token, String id, String ifMatch,
                                                    Map<String, Object> graph) {
        return client.put().uri(BASE + "/{id}", id)
            .contentType(MediaType.APPLICATION_JSON)
            .headers(headers -> {
                headers.setBearerAuth(token);
                headers.setIfMatch(ifMatch);
            })
            .body(Map.of("schemaVersion", 1, "graphJson", graph))
            .retrieve()
            .toEntity(new ParameterizedTypeReference<>() {
            });
    }

    private int putStatus(String token, String id, String ifMatch, Map<String, Object> graph) {
        try {
            return put(token, id, ifMatch, graph).getStatusCode().value();
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value();
        }
    }

    private String versionStatus(String id, int versionNumber) {
        return jdbcTemplate.queryForObject("""
            SELECT validation_status FROM agents_flow_versions
             WHERE flow_id = ? AND version_number = ?
            """, String.class, UUID.fromString(id), versionNumber);
    }

    private Map<String, Object> validGraph(String agentKey) {
        return Map.of("schemaVersion", 1,
            "nodes", List.of(
                Map.of("id", "t", "kind", "task", "x", 0.0, "y", 0.0),
                Map.of("id", "a", "kind", "agent", "agentKey", agentKey, "x", 0.5, "y", 0.5),
                Map.of("id", "r", "kind", "result", "x", 1.0, "y", 1.0)),
            "edges", List.of(Map.of("from", "t", "to", "a"), Map.of("from", "a", "to", "r")));
    }

    private Map<String, Object> invalidGraph() {
        return Map.of("schemaVersion", 1,
            "nodes", List.of(
                Map.of("id", "t", "kind", "task", "x", 0.0, "y", 0.0),
                Map.of("id", "a", "kind", "agent", "agentKey", "policies", "x", 0.5, "y", 0.5)),
            "edges", List.of(Map.of("from", "t", "to", "a")));
    }

    private Map<String, Object> fanOutWithMerge() {
        return Map.of("schemaVersion", 1,
            "nodes", List.of(
                Map.of("id", "t", "kind", "task", "x", 0.0, "y", 0.0),
                Map.of("id", "a", "kind", "agent", "agentKey", "policies", "x", 0.3, "y", 0.3),
                Map.of("id", "b", "kind", "agent", "agentKey", "writing", "x", 0.3, "y", 0.7),
                Map.of("id", "m", "kind", "merge", "x", 0.6, "y", 0.5),
                Map.of("id", "r", "kind", "result", "x", 1.0, "y", 0.5)),
            "edges", List.of(
                Map.of("from", "t", "to", "a"), Map.of("from", "t", "to", "b"),
                Map.of("from", "a", "to", "m"), Map.of("from", "b", "to", "m"),
                Map.of("from", "m", "to", "r")));
    }

    private Map<String, Object> fanOutWithoutMerge() {
        return Map.of("schemaVersion", 1,
            "nodes", List.of(
                Map.of("id", "t", "kind", "task", "x", 0.0, "y", 0.0),
                Map.of("id", "a", "kind", "agent", "agentKey", "policies", "x", 0.3, "y", 0.3),
                Map.of("id", "b", "kind", "agent", "agentKey", "writing", "x", 0.3, "y", 0.7),
                Map.of("id", "r", "kind", "result", "x", 1.0, "y", 0.5)),
            "edges", List.of(
                Map.of("from", "t", "to", "a"), Map.of("from", "t", "to", "b"),
                Map.of("from", "a", "to", "r"), Map.of("from", "b", "to", "r")));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> errors(Map<String, Object> validation) {
        return (List<Map<String, Object>>) validation.get("errors");
    }

    private static List<String> codes(Map<String, Object> validation) {
        return errors(validation).stream().map(error -> (String) error.get("code")).toList();
    }

    private String login(String email) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", PASSWORD))
            .retrieve().body(Map.class);
        return response == null ? null : (String) response.get("token");
    }
}
