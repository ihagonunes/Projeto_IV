package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.GovernanceApplication;
import br.com.qsoft.governance.aigateway.application.CreateModel;
import br.com.qsoft.governance.aigateway.application.CreateModelCommand;
import br.com.qsoft.governance.aigateway.application.CreateProvider;
import br.com.qsoft.governance.aigateway.application.CreateProviderCommand;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.aigateway.domain.provider.ProviderKind;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.support.TestTenants;
import java.math.BigDecimal;
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
 * BE-06: execução síncrona com o provider STUB real, idempotência, recibos,
 * isolamento por tenant e mapeamento de falha/timeout.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = GovernanceApplication.class)
class AgentsFlowExecutionIT {

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
    private static final String BASE = "/api/v1/agentes";

    @LocalServerPort
    int port;

    @Autowired
    TestTenants testTenants;

    @Autowired
    CreateProvider createProvider;

    @Autowired
    CreateModel createModel;

    @Autowired
    FinancialTestBudgets financialBudgets;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private RestClient client;
    private Tenant tenant;
    private Tenant otherTenant;
    private String adminEmail;
    private String managerEmail;
    private String otherAdminEmail;
    private UUID department;
    private Model echoModel;
    private Model errorModel;
    private Model timeoutModel;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        String slug = "exec" + UUID.randomUUID().toString().substring(0, 8).toLowerCase();
        adminEmail = "admin@" + slug + ".com";
        managerEmail = "manager@" + slug + ".com";
        otherAdminEmail = "admin@" + slug + "b.com";
        tenant = testTenants.ensureTenant(slug, "Execução de agentes", "Admin", adminEmail,
            PASSWORD);
        otherTenant = testTenants.ensureTenant(slug + "b", "Execução B", "Admin B",
            otherAdminEmail, PASSWORD);
        department = createDepartment();
        testTenants.ensureMember(tenant, testTenants.admin(tenant, adminEmail).id(),
            managerEmail, "Manager", PASSWORD, HierarchyLevel.MANAGER, department, "MANAGER");

        UUID providerId = createProvider.create(new CreateProviderCommand(
            tenant.id(), "Stub " + UUID.randomUUID(), ProviderKind.STUB, null, null, null)).id();
        echoModel = model(providerId, "stub-echo");
        errorModel = model(providerId, "stub-error");
        timeoutModel = model(providerId, "stub-timeout");
        setDefaultModel(echoModel);
        financialBudgets.configure(tenant, adminEmail, PASSWORD);
    }

    @Test
    void executesFiveAgentFlowWithStepsAndReceipts() {
        String flowId = createPublishedFlow(fiveAgentGraph());

        ResponseEntity<Map<String, Object>> response = execute(login(managerEmail), flowId,
            "exec-5-agents", "Explique a política de férias e faça um resumo.");

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        Map<String, Object> execution = response.getBody();
        assertThat(execution).containsEntry("status", "SUCCEEDED");
        assertThat((String) execution.get("result")).startsWith("[stub]");
        List<Map<String, Object>> stepList = steps(execution);
        assertThat(stepList).hasSize(9);
        assertThat(stepList).filteredOn(step -> "agent".equals(step.get("nodeKind")))
            .hasSize(5).allSatisfy(step -> {
                assertThat(step.get("requestId")).isNotNull();
                assertThat(step.get("status")).isEqualTo("SUCCEEDED");
            });
        Map<String, Object> cost = (Map<String, Object>) execution.get("cost");
        assertThat(cost).containsEntry("status", "KNOWN");
        assertThat(new BigDecimal(String.valueOf(cost.get("estimatedCost"))))
            .isGreaterThan(BigDecimal.ZERO);

        assertThat(attempts("AGENT_FLOW")).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM agents_flow_executions WHERE id = ?", String.class,
            UUID.fromString((String) execution.get("id")))).isEqualTo("SUCCEEDED");

        ResponseEntity<Map<String, Object>> fetched = client.get()
            .uri(BASE + "/execucoes/{id}", execution.get("id"))
            .headers(h -> h.setBearerAuth(login(managerEmail)))
            .retrieve().toEntity(new ParameterizedTypeReference<>() {
            });
        assertThat(fetched.getStatusCode().value()).isEqualTo(200);
        assertThat(fetched.getBody()).containsEntry("id", execution.get("id"));
        assertThat(steps(fetched.getBody())).hasSize(9);
    }

    @Test
    void idempotencyReturnsTheSameExecutionWithoutNewAttempts() {
        String flowId = createPublishedFlow(simpleGraph("policies"));
        String token = login(managerEmail);

        Map<String, Object> first = execute(token, flowId, "same-key-1", "Tarefa única").getBody();
        long attempts = attempts("AGENT_FLOW");
        Map<String, Object> second = execute(token, flowId, "same-key-1", "Tarefa única").getBody();

        assertThat(second.get("id")).isEqualTo(first.get("id"));
        assertThat(attempts("AGENT_FLOW")).isEqualTo(attempts);
    }

    @Test
    void anotherTenantCannotExecuteOrRead() {
        String flowId = createPublishedFlow(simpleGraph("policies"));
        String id = (String) execute(login(managerEmail), flowId, "exec-tenant-a", "Tarefa")
            .getBody().get("id");
        String other = login(otherAdminEmail);

        assertThat(executeStatus(other, flowId, "exec-tenant-b", "Tarefa")).isEqualTo(404);
        assertThat(getStatus(other, id)).isEqualTo(404);
    }

    @Test
    void unpublishedFlowIsRejected() {
        String flowId = createFlow(simpleGraph("policies"));

        assertThat(executeStatus(login(managerEmail), flowId, "exec-draft-1", "Tarefa"))
            .isEqualTo(409);
    }

    @Test
    void providerFailureMarksTheExecutionFailed() {
        setDefaultModel(errorModel);
        String flowId = createPublishedFlow(simpleGraph("policies"));

        Map<String, Object> execution = execute(login(managerEmail), flowId, "exec-error-1",
            "Tarefa").getBody();

        assertThat(execution).containsEntry("status", "FAILED");
        assertThat(execution.get("errorCode")).isEqualTo("AGENT_COMPLETION_FAILED");
        assertThat(execution.get("result")).isNull();
        assertThat(steps(execution)).filteredOn(step -> "agent".equals(step.get("nodeKind")))
            .extracting(step -> step.get("status")).contains("FAILED");
    }

    @Test
    void providerTimeoutMarksTheExecutionTimedOut() {
        setDefaultModel(timeoutModel);
        String flowId = createPublishedFlow(simpleGraph("policies"));

        Map<String, Object> execution = execute(login(managerEmail), flowId, "exec-timeout-1",
            "Tarefa").getBody();

        assertThat(execution).containsEntry("status", "TIMED_OUT");
        assertThat(steps(execution)).filteredOn(step -> "agent".equals(step.get("nodeKind")))
            .extracting(step -> step.get("status")).contains("TIMED_OUT");
    }

    @Test
    void invalidInputAndMissingIdempotencyKeyAreBadRequests() {
        String flowId = createPublishedFlow(simpleGraph("policies"));
        String token = login(managerEmail);

        assertThat(executeStatus(token, flowId, null, "Tarefa")).isEqualTo(400);
        assertThat(executeStatus(token, flowId, "exec-invalid", " ")).isEqualTo(400);
        assertThat(executeStatus(token, flowId, "short", "Tarefa")).isEqualTo(400);
    }

    private Map<String, Object> simpleGraph(String agentKey) {
        return graph(List.of(
            node("t", "task", null, 0.0), node("a", "agent", agentKey, 0.5),
            node("r", "result", null, 1.0)),
            List.of(edge("t", "a"), edge("a", "r")));
    }

    private Map<String, Object> fiveAgentGraph() {
        return graph(List.of(
            node("t", "task", null, 0.0),
            node("policies", "agent", "policies", 0.2),
            node("writing", "agent", "writing", 0.2),
            node("m1", "merge", null, 0.4),
            node("data", "agent", "data", 0.55),
            node("research", "agent", "research", 0.55),
            node("m2", "merge", null, 0.7),
            node("review", "agent", "review", 0.85),
            node("r", "result", null, 1.0)),
            List.of(edge("t", "policies"), edge("t", "writing"),
                edge("policies", "m1"), edge("writing", "m1"),
                edge("m1", "data"), edge("t", "research"),
                edge("data", "m2"), edge("research", "m2"),
                edge("m2", "review"), edge("review", "r")));
    }

    private Map<String, Object> graph(List<Map<String, Object>> nodes,
                                      List<Map<String, Object>> edges) {
        Map<String, Object> graph = new HashMap<>();
        graph.put("schemaVersion", 1);
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        return graph;
    }

    private Map<String, Object> node(String id, String kind, String agentKey, double x) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("kind", kind);
        if (agentKey != null) node.put("agentKey", agentKey);
        node.put("x", x);
        node.put("y", 0.5);
        return node;
    }

    private Map<String, Object> edge(String from, String to) {
        return Map.of("from", from, "to", to);
    }

    private String createPublishedFlow(Map<String, Object> graph) {
        String id = createFlow(graph);
        ResponseEntity<Map<String, Object>> published = client.post()
            .uri(BASE + "/fluxos/{id}/publicar", id)
            .headers(h -> {
                h.setBearerAuth(login(managerEmail));
                h.setIfMatch("\"1\"");
            })
            .retrieve().toEntity(new ParameterizedTypeReference<>() {
            });
        assertThat(published.getStatusCode().value()).isEqualTo(200);
        return id;
    }

    private String createFlow(Map<String, Object> graph) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Fluxo de execução");
        body.put("schemaVersion", 1);
        body.put("graphJson", graph);
        ResponseEntity<Map<String, Object>> response = client.post().uri(BASE + "/fluxos")
            .contentType(MediaType.APPLICATION_JSON)
            .headers(h -> h.setBearerAuth(login(managerEmail)))
            .body(body)
            .retrieve().toEntity(new ParameterizedTypeReference<>() {
            });
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return (String) response.getBody().get("id");
    }

    private ResponseEntity<Map<String, Object>> execute(String token, String flowId, String key,
                                                        String input) {
        var spec = client.post().uri(BASE + "/fluxos/{id}/execucoes", flowId)
            .contentType(MediaType.APPLICATION_JSON)
            .headers(h -> {
                h.setBearerAuth(token);
                if (key != null) h.set("Idempotency-Key", key);
            })
            .body(Map.of("input", input));
        return spec.retrieve().toEntity(new ParameterizedTypeReference<>() {
        });
    }

    private int executeStatus(String token, String flowId, String key, String input) {
        try {
            return execute(token, flowId, key, input).getStatusCode().value();
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value();
        }
    }

    private int getStatus(String token, String executionId) {
        try {
            return client.get().uri(BASE + "/execucoes/{id}", executionId)
                .headers(h -> h.setBearerAuth(token))
                .retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> steps(Map<String, Object> execution) {
        return (List<Map<String, Object>>) execution.get("steps");
    }

    private long attempts(String purpose) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM aigateway_request_attempts
             WHERE tenant_id = ? AND purpose = ? AND execution_status = 'SUCCEEDED'
            """, Long.class, tenant.id().value(), purpose);
        return count == null ? 0 : count;
    }

    private UUID createDepartment() {
        ResponseEntity<Map<String, Object>> response = client.post().uri("/api/v1/departamentos")
            .contentType(MediaType.APPLICATION_JSON)
            .headers(h -> h.setBearerAuth(login(adminEmail)))
            .body(Map.of("name", "Depto Execução " + UUID.randomUUID()))
            .retrieve().toEntity(new ParameterizedTypeReference<>() {
            });
        return UUID.fromString((String) response.getBody().get("id"));
    }

    private Model model(UUID providerId, String externalName) {
        return createModel.create(new CreateModelCommand(tenant.id(), providerId,
            "model " + UUID.randomUUID(), externalName, new BigDecimal("0.01"),
            new BigDecimal("0.03")));
    }

    private void setDefaultModel(Model model) {
        ResponseEntity<Void> response = client.put()
            .uri("/api/v1/ai-gateway/departments/{id}/model-policy", department)
            .contentType(MediaType.APPLICATION_JSON)
            .headers(h -> h.setBearerAuth(login(adminEmail)))
            .body(Map.of("allowedModelIds", List.of(echoModel.id().toString(),
                    errorModel.id().toString(), timeoutModel.id().toString()),
                "defaultModelId", model.id().toString()))
            .retrieve().toBodilessEntity();
        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    private String login(String email) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", PASSWORD))
            .retrieve().body(Map.class);
        return response == null ? null : (String) response.get("token");
    }
}
