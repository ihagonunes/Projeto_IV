package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.GovernanceApplication;
import br.com.qsoft.governance.agents.application.port.AgentFlowRepository;
import br.com.qsoft.governance.agents.application.port.AgentFlowVersionRepository;
import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.agents.domain.AgentFlowVersion;
import br.com.qsoft.governance.agents.domain.AgentValidationStatus;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.support.TestTenants;
import java.time.Instant;
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
 * Ciclo de vida do fluxo (ADR-44): CRUD, versão imutável, `If-Match`/412,
 * soft delete, paginação, escopo SELF/DEPARTMENT/TENANT e Tenant A ≠ B.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = GovernanceApplication.class)
class AgentsFlowsIT {

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

    private static final String SLUG = "agentsflows";
    private static final String OTHER_SLUG = "agentsflowsb";
    private static final String ADMIN_EMAIL = "admin@agentsflows.com";
    private static final String MANAGER1_EMAIL = "manager1@agentsflows.com";
    private static final String MANAGER2_EMAIL = "manager2@agentsflows.com";
    private static final String COLLAB_EMAIL = "collab@agentsflows.com";
    private static final String OTHER_ADMIN_EMAIL = "admin@agentsflowsb.com";
    private static final String PASSWORD = "secret123";
    private static final UUID DEPARTMENT1 = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID DEPARTMENT2 = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String BASE = "/api/v1/agentes/fluxos";

    @LocalServerPort
    int port;

    @Autowired
    TestTenants testTenants;

    @Autowired
    AgentFlowRepository flowRepository;

    @Autowired
    AgentFlowVersionRepository versionRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private RestClient client;
    private Tenant tenant;
    private Tenant otherTenant;
    private User admin;
    private User manager1;
    private User manager2;
    private User collaborator;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        tenant = testTenants.ensureTenant(SLUG, "Agents Flows", "Admin", ADMIN_EMAIL, PASSWORD);
        var adminId = testTenants.admin(tenant, ADMIN_EMAIL).id();
        admin = testTenants.admin(tenant, ADMIN_EMAIL);
        manager1 = testTenants.ensureMember(tenant, adminId, MANAGER1_EMAIL, "Manager 1",
            PASSWORD, HierarchyLevel.MANAGER, DEPARTMENT1, "MANAGER");
        manager2 = testTenants.ensureMember(tenant, adminId, MANAGER2_EMAIL, "Manager 2",
            PASSWORD, HierarchyLevel.MANAGER, DEPARTMENT2, "MANAGER");
        collaborator = testTenants.ensureMember(tenant, adminId, COLLAB_EMAIL, "Collab",
            PASSWORD, HierarchyLevel.COLLABORATOR, null, "COLLABORATOR");
        otherTenant = testTenants.ensureTenant(OTHER_SLUG, "Agents Flows B", "Admin B",
            OTHER_ADMIN_EMAIL, PASSWORD);

        jdbcTemplate.update("DELETE FROM agents_flow_versions");
        jdbcTemplate.update("DELETE FROM agents_flows");
    }

    @Test
    void managerCreatesFlowWithServerIdentityAndDefaultDepartment() {
        Map<String, Object> body = createBody("Fluxo básico", "v1");
        body.put("id", UUID.randomUUID().toString());
        body.put("tenantId", otherTenant.id().value().toString());
        body.put("ownerUserId", admin.id().value().toString());
        body.put("versionNumber", 99);

        ResponseEntity<Map<String, Object>> response = create(login(MANAGER1_EMAIL), body);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"1\"");
        Map<String, Object> flow = response.getBody();
        assertThat(flow).containsEntry("status", "DRAFT")
            .containsEntry("rowVersion", 1)
            .containsEntry("ownerUserId", manager1.id().value().toString())
            .containsEntry("departmentId", DEPARTMENT1.toString())
            .containsEntry("name", "Fluxo básico");
        assertThat(flow.get("id")).isNotEqualTo(body.get("id"));
        Map<String, Object> version = version(flow);
        assertThat(version).containsEntry("versionNumber", 1)
            .containsEntry("schemaVersion", 1)
            .containsEntry("validationStatus", "INVALID");
        assertThat(graph(version)).containsEntry("marker", "v1");
    }

    @Test
    void managerCannotCreateInAnotherDepartment() {
        Map<String, Object> body = createBody("Outro depto", "v1");
        body.put("departmentId", DEPARTMENT2.toString());

        assertThat(createStatus(login(MANAGER1_EMAIL), body)).isEqualTo(403);
    }

    @Test
    void putCreatesImmutableVersionAndBumpsRowVersion() {
        String token = login(MANAGER1_EMAIL);
        Map<String, Object> created = create(token, createBody("Fluxo", "v1")).getBody();
        String id = (String) created.get("id");

        ResponseEntity<Map<String, Object>> response = put(token, id, "\"1\"", graphBody("v2"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"2\"");
        assertThat(response.getBody()).containsEntry("rowVersion", 2);
        Map<String, Object> version = version(response.getBody());
        assertThat(version).containsEntry("versionNumber", 2);
        assertThat(graph(version)).containsEntry("marker", "v2");

        String persistedV1 = jdbcTemplate.queryForObject("""
            SELECT graph_json::text FROM agents_flow_versions
             WHERE flow_id = ? AND version_number = 1
            """, String.class, UUID.fromString(id));
        assertThat(persistedV1).contains("v1");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM agents_flow_versions WHERE flow_id = ?",
            Integer.class, UUID.fromString(id))).isEqualTo(2);
    }

    @Test
    void putRequiresPreconditionAndRejectsStaleOrConcurrentUpdate() {
        String token = login(MANAGER1_EMAIL);
        String id = (String) create(token, createBody("Fluxo", "v1")).getBody().get("id");

        assertThat(putStatus(token, id, null, graphBody("v2"))).isEqualTo(400);
        assertThat(putStatus(token, id, "\"9\"", graphBody("v2"))).isEqualTo(412);

        Map<String, Object> body = graphBody("v2");
        body.put("rowVersion", 1);
        assertThat(putStatus(token, id, null, body)).isEqualTo(200);
    }

    @Test
    void deleteIsSoftAndHidesTheFlow() {
        String token = login(MANAGER1_EMAIL);
        String id = (String) create(token, createBody("Fluxo", "v1")).getBody().get("id");

        assertThat(deleteStatus(token, id)).isEqualTo(204);
        assertThat(getStatus(token, id)).isEqualTo(404);
        assertThat(list(token, "?page=0&size=20").get("totalElements")).isEqualTo(0);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT deleted_at IS NOT NULL FROM agents_flows WHERE id = ?",
            Boolean.class, UUID.fromString(id))).isTrue();
    }

    @Test
    void flowOfAnotherTenantIsAlwaysNotFound() {
        String adminA = login(ADMIN_EMAIL);
        String id = (String) create(adminA, createBody("Fluxo do tenant A", "v1"))
            .getBody().get("id");
        String adminB = login(OTHER_ADMIN_EMAIL);

        assertThat(getStatus(adminB, id)).isEqualTo(404);
        assertThat(putStatus(adminB, id, "\"1\"", graphBody("v2"))).isEqualTo(404);
        assertThat(deleteStatus(adminB, id)).isEqualTo(404);
    }

    @Test
    void managerSeesOnlyItsDepartment() {
        String idD1 = (String) create(login(MANAGER1_EMAIL), createBody("D1", "v1"))
            .getBody().get("id");
        String idD2 = (String) create(login(MANAGER2_EMAIL), createBody("D2", "v1"))
            .getBody().get("id");

        String manager1 = login(MANAGER1_EMAIL);
        assertThat(getStatus(manager1, idD1)).isEqualTo(200);
        assertThat(getStatus(manager1, idD2)).isEqualTo(404);
        assertThat(list(manager1, "?page=0&size=20").get("totalElements")).isEqualTo(1);

        String adminToken = login(ADMIN_EMAIL);
        assertThat(list(adminToken, "?page=0&size=20").get("totalElements")).isEqualTo(2);
    }

    @Test
    void collaboratorSeesOnlyOwnFlowsAndCannotWrite() {
        Instant now = Instant.now();
        AgentFlow own = flowRepository.save(AgentFlow.create(UUID.randomUUID(), tenant.id(),
            collaborator.id(), null, "Do colaborador", now));
        versionRepository.save(tenant.id(), new AgentFlowVersion(UUID.randomUUID(), own.id(),
            1, 1, "{\"marker\":\"own\"}", AgentValidationStatus.INVALID, collaborator.id(), now));
        String ownId = own.id().toString();
        String otherId = (String) create(login(MANAGER1_EMAIL), createBody("Do gestor", "v1"))
            .getBody().get("id");

        String collaboratorToken = login(COLLAB_EMAIL);
        Map<String, Object> page = list(collaboratorToken, "?page=0&size=20");
        assertThat(page.get("totalElements")).isEqualTo(1);
        assertThat(content(page)).extracting(item -> item.get("id")).containsExactly(ownId);
        assertThat(getStatus(collaboratorToken, otherId)).isEqualTo(404);
        assertThat(getStatus(collaboratorToken, ownId)).isEqualTo(200);

        assertThat(createStatus(collaboratorToken, createBody("Novo", "v1"))).isEqualTo(403);
        assertThat(putStatus(collaboratorToken, ownId, "\"1\"", graphBody("v2"))).isEqualTo(403);
        assertThat(deleteStatus(collaboratorToken, ownId)).isEqualTo(403);
    }

    @Test
    void listPaginatesFiltersByStatusAndClampsSize() {
        String token = login(ADMIN_EMAIL);
        create(token, createBody("A", "v1"));
        create(token, createBody("B", "v1"));
        create(token, createBody("C", "v1"));

        Map<String, Object> first = list(token, "?page=0&size=2");
        assertThat(first).containsEntry("totalElements", 3)
            .containsEntry("page", 0).containsEntry("size", 2)
            .containsEntry("totalPages", 2);
        assertThat(content(first)).hasSize(2);

        Map<String, Object> clamped = list(token, "?page=0&size=500");
        assertThat(clamped).containsEntry("size", 100);

        assertThat(list(token, "?status=DRAFT&page=0&size=20").get("totalElements"))
            .isEqualTo(3);
        assertThat(list(token, "?status=ARCHIVED&page=0&size=20").get("totalElements"))
            .isEqualTo(0);
    }

    @Test
    void invalidGraphsAndUnknownFlowsAreRejected() {
        String token = login(ADMIN_EMAIL);

        Map<String, Object> invalid = new HashMap<>();
        invalid.put("name", "Inválido");
        invalid.put("schemaVersion", 1);
        invalid.put("graphJson", List.of());
        assertThat(createStatus(token, invalid)).isEqualTo(400);

        Map<String, Object> duplicated = new HashMap<>();
        duplicated.put("name", "Duplicado");
        duplicated.put("schemaVersion", 1);
        duplicated.put("graphJson", Map.of("schemaVersion", 1, "nodes",
            List.of(Map.of("id", "a"), Map.of("id", "a")), "edges", List.of()));
        assertThat(createStatus(token, duplicated)).isEqualTo(400);

        UUID unknown = UUID.randomUUID();
        assertThat(getStatus(token, unknown.toString())).isEqualTo(404);
        assertThat(putStatus(token, unknown.toString(), "\"1\"", graphBody("v2"))).isEqualTo(404);
        assertThat(deleteStatus(token, unknown.toString())).isEqualTo(404);
    }

    @Test
    void agentNodesMustReferenceTheGlobalCatalog() {
        String token = login(ADMIN_EMAIL);

        Map<String, Object> graph = new HashMap<>();
        graph.put("schemaVersion", 1);
        graph.put("nodes", List.of(Map.of("id", "n1", "kind", "agent", "agentKey", "policies")));
        graph.put("edges", List.of());
        Map<String, Object> known = new HashMap<>();
        known.put("name", "Fluxo com agente");
        known.put("schemaVersion", 1);
        known.put("graphJson", graph);
        assertThat(createStatus(token, known)).isEqualTo(201);

        Map<String, Object> unknownGraph = new HashMap<>(graph);
        unknownGraph.put("nodes", List.of(Map.of("id", "n1", "kind", "agent", "agentKey", "merge")));
        Map<String, Object> unknown = new HashMap<>();
        unknown.put("name", "Fluxo sem catálogo");
        unknown.put("schemaVersion", 1);
        unknown.put("graphJson", unknownGraph);
        // Erro semântico não bloqueia o rascunho: é persistido como INVALID e
        // a publicação será recusada (ver AgentsFlowValidationIT).
        ResponseEntity<Map<String, Object>> created = create(token, unknown);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(version(created.getBody())).containsEntry("validationStatus", "INVALID");
    }

    private Map<String, Object> createBody(String name, String marker) {
        Map<String, Object> body = graphBody(marker);
        body.put("name", name);
        return body;
    }

    private Map<String, Object> graphBody(String marker) {
        Map<String, Object> graph = new HashMap<>();
        graph.put("schemaVersion", 1);
        graph.put("marker", marker);
        graph.put("nodes", List.of(Map.of("id", "step-1", "kind", "task")));
        graph.put("edges", List.of());
        Map<String, Object> body = new HashMap<>();
        body.put("schemaVersion", 1);
        body.put("graphJson", graph);
        return body;
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

    private int createStatus(String token, Map<String, Object> body) {
        try {
            return create(token, body).getStatusCode().value();
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value();
        }
    }

    private ResponseEntity<Map<String, Object>> put(String token, String id, String ifMatch,
                                                    Map<String, Object> body) {
        var spec = client.put().uri(BASE + "/{id}", id)
            .contentType(MediaType.APPLICATION_JSON)
            .headers(headers -> headers.setBearerAuth(token))
            .body(body);
        if (ifMatch != null) {
            spec = spec.headers(headers -> headers.setIfMatch(ifMatch));
        }
        return spec.retrieve().toEntity(new ParameterizedTypeReference<>() {
        });
    }

    private int putStatus(String token, String id, String ifMatch, Map<String, Object> body) {
        try {
            return put(token, id, ifMatch, body).getStatusCode().value();
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value();
        }
    }

    private int getStatus(String token, String id) {
        try {
            return client.get().uri(BASE + "/{id}", id)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value();
        }
    }

    private int deleteStatus(String token, String id) {
        try {
            return client.delete().uri(BASE + "/{id}", id)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> list(String token, String query) {
        return client.get().uri(BASE + query)
            .headers(headers -> headers.setBearerAuth(token))
            .retrieve().body(new ParameterizedTypeReference<>() {
            });
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> content(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("content");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> version(Map<String, Object> flow) {
        return (Map<String, Object>) flow.get("currentVersion");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> graph(Map<String, Object> version) {
        return (Map<String, Object>) version.get("graph");
    }

    private String login(String email) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", PASSWORD))
            .retrieve().body(Map.class);
        return response == null ? null : (String) response.get("token");
    }
}
