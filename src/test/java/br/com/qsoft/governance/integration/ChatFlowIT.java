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
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.support.TestTenants;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
 * Fluxo do chat (ADR-39/SPEC-04): recibo ligado à tentativa e ao UsageRecord,
 * falha do provider, AZ12 (dono = ator), isolamento de tenant/paginação e
 * ausência de conteúdo em log.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = GovernanceApplication.class)
class ChatFlowIT {

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
    private Tenant tenant;
    private Tenant other;
    private String adminEmail;
    private String managerEmail;
    private String executiveEmail;
    private String collabEmail;
    private String otherAdminEmail;
    private String otherCollabEmail;
    private Model echoModel;
    private Model errorModel;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        String slug = "chat" + UUID.randomUUID().toString().substring(0, 8).toLowerCase();
        adminEmail = "admin@" + slug + ".com";
        otherAdminEmail = "admin@" + slug + "x.com";
        tenant = testTenants.ensureTenant(slug, "Chat Demo", "Admin", adminEmail, PASSWORD);
        other = testTenants.ensureTenant(slug + "x", "Chat Other", "Admin",
            otherAdminEmail, PASSWORD);

        UUID departmentId = createDepartment();

        managerEmail = "manager@" + slug + ".com";
        executiveEmail = "executive@" + slug + ".com";
        collabEmail = "collab@" + slug + ".com";
        User admin = testTenants.admin(tenant, adminEmail);
        testTenants.ensureMember(tenant, admin.id(), managerEmail, "Manager", PASSWORD,
            HierarchyLevel.MANAGER, departmentId, "MANAGER");
        testTenants.ensureMember(tenant, admin.id(), executiveEmail, "Executive", PASSWORD,
            HierarchyLevel.EXECUTIVE, departmentId, "EXECUTIVE");
        testTenants.ensureMember(tenant, admin.id(), collabEmail, "Collab", PASSWORD,
            HierarchyLevel.COLLABORATOR, departmentId, "COLLABORATOR");

        otherCollabEmail = "collab@" + slug + "x.com";
        User otherAdmin = testTenants.admin(other, otherAdminEmail);
        testTenants.ensureMember(other, otherAdmin.id(), otherCollabEmail, "Other Collab",
            PASSWORD, HierarchyLevel.COLLABORATOR, UUID.randomUUID(), "COLLABORATOR");

        UUID providerId = createProvider.create(new CreateProviderCommand(
            tenant.id(), "Stub " + UUID.randomUUID(), ProviderKind.STUB, null, null, null)).id();
        echoModel = createModel.create(new CreateModelCommand(
            tenant.id(), providerId, "echo " + UUID.randomUUID(), "stub-echo",
            new BigDecimal("0.01"), new BigDecimal("0.03")));
        errorModel = createModel.create(new CreateModelCommand(
            tenant.id(), providerId, "error " + UUID.randomUUID(), "stub-error",
            new BigDecimal("0.01"), new BigDecimal("0.03")));
        assertThat(putStatus(adminEmail,
            "/api/v1/ai-gateway/departments/" + departmentId + "/model-policy",
            Map.of("allowedModelIds",
                    List.of(echoModel.id().toString(), errorModel.id().toString()),
                "defaultModelId", echoModel.id().toString())))
            .isEqualTo(204);
    }

    @Test
    void fluxoCompletoComStubReciboBateComTentativaEUsageRecord() {
        Map<String, Object> created = post(managerEmail, "/api/v1/conversas",
            Map.of("mensagem", "Olá, preciso de ajuda com políticas"));

        String conversationId = (String) ((Map<?, ?>) created.get("conversa")).get("id");
        Map<?, ?> receipt = (Map<?, ?>) created.get("recibo");
        String requestId = (String) receipt.get("requestId");
        assertThat(conversationId).isNotBlank();
        assertThat(((List<?>) created.get("mensagens"))).hasSize(2);

        Map<String, Object> attempt = jdbcTemplate.queryForMap(
            "SELECT execution_status, input_tokens, output_tokens, cost_status, estimated_cost "
                + "FROM aigateway_request_attempts WHERE request_id = ?", UUID.fromString(requestId));
        assertThat(attempt.get("execution_status")).isEqualTo("SUCCEEDED");
        assertThat(((Number) attempt.get("input_tokens")).longValue())
            .isEqualTo(((Number) receipt.get("tokensEntrada")).longValue());
        assertThat(((Number) attempt.get("output_tokens")).longValue())
            .isEqualTo(((Number) receipt.get("tokensSaida")).longValue());
        assertThat(attempt.get("cost_status")).isEqualTo("KNOWN");
        assertThat((BigDecimal) attempt.get("estimated_cost"))
            .isEqualByComparingTo(new BigDecimal(receipt.get("custoEstimado").toString()));

        Integer usage = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM observability_usage_records "
                + "WHERE request_id = ? AND tenant_id = ?",
            Integer.class, UUID.fromString(requestId), tenant.id().value());
        assertThat(usage).isEqualTo(1);

        Map<String, Object> list = get(managerEmail, "/api/v1/conversas");
        assertThat(list.get("totalElements")).isEqualTo(1);

        List<?> messages = getList(managerEmail,
            "/api/v1/conversas/" + conversationId + "/mensagens");
        assertThat(messages).hasSize(2);

        Map<String, Object> turn = post(managerEmail,
            "/api/v1/conversas/" + conversationId + "/mensagens",
            Map.of("mensagem", "E agora?"));
        assertThat(((List<?>) turn.get("mensagens"))).hasSize(2);

        assertThat(getList(managerEmail,
            "/api/v1/conversas/" + conversationId + "/mensagens")).hasSize(4);
    }

    @Test
    void falhaDoProviderMantemUserSemAssistant() {
        Map<String, Object> request = Map.of(
            "mensagem", "mensagem que falha",
            "requestedModelId", errorModel.id().toString());

        assertThat(postStatus(managerEmail, "/api/v1/conversas", request)).isEqualTo(502);

        Map<String, Object> list = get(managerEmail, "/api/v1/conversas");
        assertThat(list.get("totalElements")).isEqualTo(1);
        String conversationId = (String) ((Map<?, ?>) ((List<?>) list.get("content")).get(0))
            .get("id");

        List<?> messages = getList(managerEmail,
            "/api/v1/conversas/" + conversationId + "/mensagens");
        assertThat(messages).hasSize(1);
        assertThat(((Map<?, ?>) messages.get(0)).get("role")).isEqualTo("USER");
    }

    @Test
    void az12_conversaDeOutraPessoaRetorna404EAdminSemChatRecebe403() {
        Map<String, Object> created = post(managerEmail, "/api/v1/conversas",
            Map.of("mensagem", "conversa do manager"));
        String conversationId = (String) ((Map<?, ?>) created.get("conversa")).get("id");

        assertThat(status(executiveEmail,
            "/api/v1/conversas/" + conversationId + "/mensagens")).isEqualTo(404);

        Map<String, Object> collabConversation = post(collabEmail, "/api/v1/conversas",
            Map.of("mensagem", "conversa do colaborador"));
        String collabId = (String) ((Map<?, ?>) collabConversation.get("conversa")).get("id");
        assertThat(status(managerEmail,
            "/api/v1/conversas/" + collabId + "/mensagens")).isEqualTo(404);

        assertThat(status(adminEmail, "/api/v1/conversas")).isEqualTo(403);
    }

    @Test
    void isolamentoDeTenantEPaginacao() {
        post(managerEmail, "/api/v1/conversas", Map.of("mensagem", "primeira"));
        post(managerEmail, "/api/v1/conversas", Map.of("mensagem", "segunda"));
        post(collabEmail, "/api/v1/conversas", Map.of("mensagem", "do colaborador"));

        Map<String, Object> firstPage = get(managerEmail, "/api/v1/conversas?page=0&size=1");
        assertThat((List<?>) firstPage.get("content")).hasSize(1);
        assertThat(firstPage.get("totalElements")).isEqualTo(2);

        Map<String, Object> secondPage = get(managerEmail, "/api/v1/conversas?page=1&size=1");
        assertThat((List<?>) secondPage.get("content")).hasSize(1);

        Map<String, Object> otherList = get(otherCollabEmail, "/api/v1/conversas");
        assertThat(otherList.get("totalElements")).isEqualTo(0);
    }

    @Test
    void conteudoNuncaVaiParaLog() {
        String prompt = "segredo-do-prompt-" + UUID.randomUUID();
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            post(managerEmail, "/api/v1/conversas", Map.of("mensagem", prompt));
        } finally {
            root.detachAppender(appender);
        }

        assertThat(appender.list)
            .noneMatch(event -> event.getFormattedMessage().contains(prompt));
    }

    @Test
    void mensagemVaziaRetorna400() {
        assertThat(postStatus(managerEmail, "/api/v1/conversas",
            Map.of("mensagem", ""))).isEqualTo(400);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID createDepartment() {
        Map<String, Object> response = post(adminEmail, "/api/v1/departamentos",
            Map.of("name", "Chat Dept " + UUID.randomUUID()));
        return UUID.fromString((String) response.get("id"));
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

    private Map<String, Object> get(String email, String uri) {
        return client.get().uri(uri)
            .header("Authorization", "Bearer " + login(email))
            .retrieve().body(new org.springframework.core.ParameterizedTypeReference<>() {
            });
    }

    private List<?> getList(String email, String uri) {
        return client.get().uri(uri)
            .header("Authorization", "Bearer " + login(email))
            .retrieve().body(new org.springframework.core.ParameterizedTypeReference<>() {
            });
    }

    private int status(String email, String uri) {
        try {
            client.get().uri(uri)
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
}
