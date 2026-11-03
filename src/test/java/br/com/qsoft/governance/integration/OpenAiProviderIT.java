package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.GovernanceApplication;
import br.com.qsoft.governance.aigateway.application.CreateModel;
import br.com.qsoft.governance.aigateway.application.CreateModelCommand;
import br.com.qsoft.governance.aigateway.application.CreateProvider;
import br.com.qsoft.governance.aigateway.application.CreateProviderCommand;
import br.com.qsoft.governance.aigateway.domain.provider.ProviderKind;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.support.TestTenants;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
 * PR03/PR05: provider OPENAI_COMPATIBLE contra um HttpServer local do JDK,
 * ponta a ponta até recibo e UsageRecord (sem SDK e sem dependência nova).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = GovernanceApplication.class)
class OpenAiProviderIT {

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
    CreateProvider createProvider;

    @Autowired
    CreateModel createModel;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private RestClient client;
    private HttpServer server;
    private String providerBaseUrl;
    private String adminEmail;
    private String managerEmail;
    private Tenant tenant;

    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private volatile int failStatus;
    private volatile long delayMs;

    @BeforeEach
    void setUp() throws IOException {
        authorization.set(null);
        requestBody.set(null);
        failStatus = 0;
        delayMs = 0;

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8));
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failStatus > 0) {
                exchange.sendResponseHeaders(failStatus, -1);
                exchange.close();
                return;
            }
            byte[] response = """
                {
                  "choices": [{"message": {"content": "resposta-http"}}],
                  "usage": {"prompt_tokens": 11, "completion_tokens": 7}
                }
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        providerBaseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        String slug = "oai" + UUID.randomUUID().toString().substring(0, 8).toLowerCase();
        adminEmail = "admin@" + slug + ".com";
        managerEmail = "manager@" + slug + ".com";
        tenant = testTenants.ensureTenant(slug, "OpenAI Demo", "Admin", adminEmail, PASSWORD);

        UUID departmentId = UUID.fromString((String) post(adminEmail, "/api/v1/departamentos",
            Map.of("name", "Dept " + UUID.randomUUID())).get("id"));
        testTenants.ensureMember(tenant, testTenants.admin(tenant, adminEmail).id(), managerEmail,
            "Manager", PASSWORD, HierarchyLevel.MANAGER, departmentId, "MANAGER");

        UUID providerId = createProvider.create(new CreateProviderCommand(
            tenant.id(), "HTTP " + UUID.randomUUID(), ProviderKind.OPENAI_COMPATIBLE,
            providerBaseUrl, null, 1000)).id();
        UUID modelId = createModel.create(new CreateModelCommand(
            tenant.id(), providerId, "remote " + UUID.randomUUID(), "remote-model",
            new BigDecimal("0.01"), new BigDecimal("0.03"))).id();

        assertThat(putStatus(adminEmail,
            "/api/v1/ai-gateway/departments/" + departmentId + "/model-policy",
            Map.of("allowedModelIds", List.of(modelId.toString()),
                "defaultModelId", modelId.toString())))
            .isEqualTo(204);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void pr05_chatAteProviderHttpReciboEUsageRecord() {
        Map<String, Object> created = post(managerEmail, "/api/v1/conversas",
            Map.of("mensagem", "olá provider"));

        List<?> messages = (List<?>) created.get("mensagens");
        assertThat(messages).hasSize(2);
        assertThat(((Map<?, ?>) messages.get(1)).get("content")).isEqualTo("resposta-http");

        Map<?, ?> receipt = (Map<?, ?>) created.get("recibo");
        String requestId = (String) receipt.get("requestId");
        assertThat(receipt.get("statusCusto")).isEqualTo("KNOWN");
        assertThat(receipt.get("tokensEntrada")).isEqualTo(11);
        assertThat(receipt.get("tokensSaida")).isEqualTo(7);
        assertThat(new BigDecimal(receipt.get("custoEstimado").toString()))
            .isEqualByComparingTo("0.000320");

        assertThat(authorization.get()).isNull();
        assertThat(requestBody.get())
            .contains("\"model\":\"remote-model\"")
            .contains("\"content\":\"olá provider\"");

        Map<String, Object> usage = jdbcTemplate.queryForMap(
            "SELECT input_tokens, output_tokens, estimated_cost "
                + "FROM observability_usage_records WHERE request_id = ?",
            UUID.fromString(requestId));
        assertThat(((Number) usage.get("input_tokens")).longValue()).isEqualTo(11);
        assertThat(((Number) usage.get("output_tokens")).longValue()).isEqualTo(7);
        assertThat((BigDecimal) usage.get("estimated_cost"))
            .isEqualByComparingTo("0.000320");

        Map<String, Object> attempt = jdbcTemplate.queryForMap(
            "SELECT cost_status, error_code FROM aigateway_request_attempts WHERE request_id = ?",
            UUID.fromString(requestId));
        assertThat(attempt.get("cost_status")).isEqualTo("KNOWN");
        assertThat(attempt.get("error_code")).isNull();
    }

    @Test
    void pr03_http429Vira502ComCodigoCurto() {
        failStatus = 429;

        assertThat(postStatus(managerEmail, "/api/v1/conversas",
            Map.of("mensagem", "falha 429"))).isEqualTo(502);
        assertThat(postErrorBody(managerEmail, "/api/v1/conversas",
            Map.of("mensagem", "falha 429"))).contains("PROVIDER_FAILURE");

        Map<String, Object> attempt = jdbcTemplate.queryForMap(
            "SELECT cost_status, error_code FROM aigateway_request_attempts "
                + "ORDER BY created_at DESC LIMIT 1");
        assertThat(attempt.get("cost_status")).isEqualTo("INCONCLUSIVE");
        assertThat(attempt.get("error_code")).isEqualTo("HTTP_429");
    }

    @Test
    void pr03_http500Vira502ComCodigoCurto() {
        failStatus = 500;

        assertThat(postStatus(managerEmail, "/api/v1/conversas",
            Map.of("mensagem", "falha 500"))).isEqualTo(502);
        assertThat(postErrorBody(managerEmail, "/api/v1/conversas",
            Map.of("mensagem", "falha 500"))).contains("PROVIDER_FAILURE");

        Map<String, Object> attempt = jdbcTemplate.queryForMap(
            "SELECT cost_status, error_code FROM aigateway_request_attempts "
                + "ORDER BY created_at DESC LIMIT 1");
        assertThat(attempt.get("cost_status")).isEqualTo("INCONCLUSIVE");
        assertThat(attempt.get("error_code")).isEqualTo("HTTP_500");
    }

    @Test
    void pr03_timeoutVira504() {
        delayMs = 2500;

        assertThat(postStatus(managerEmail, "/api/v1/conversas",
            Map.of("mensagem", "timeout"))).isEqualTo(504);
        assertThat(postErrorBody(managerEmail, "/api/v1/conversas",
            Map.of("mensagem", "timeout"))).contains("PROVIDER_TIMEOUT");

        Map<String, Object> attempt = jdbcTemplate.queryForMap(
            "SELECT execution_status, cost_status, error_code "
                + "FROM aigateway_request_attempts ORDER BY created_at DESC LIMIT 1");
        assertThat(attempt.get("execution_status")).isEqualTo("TIMED_OUT");
        assertThat(attempt.get("cost_status")).isEqualTo("INCONCLUSIVE");
        assertThat(attempt.get("error_code")).isEqualTo("TIMEOUT");
    }

    private Map<String, Object> post(String email, String uri, Object body) {
        return client.post().uri(uri)
            .header("Authorization", "Bearer " + login(email))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve().body(new org.springframework.core.ParameterizedTypeReference<>() {
            });
    }

    private int postStatus(String email, String uri, Object body) {
        try {
            post(email, uri, body);
            return 201;
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value();
        }
    }

    private String postErrorBody(String email, String uri, Object body) {
        try {
            post(email, uri, body);
            return "";
        } catch (RestClientResponseException ex) {
            return ex.getResponseBodyAsString();
        }
    }

    private int putStatus(String email, String uri, Object body) {
        try {
            client.put().uri(uri)
                .header("Authorization", "Bearer " + login(email))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().toBodilessEntity();
            return 204;
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
}
