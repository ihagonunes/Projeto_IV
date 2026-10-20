package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.GovernanceApplication;
import br.com.qsoft.governance.aigateway.application.CreateModel;
import br.com.qsoft.governance.aigateway.application.CreateModelCommand;
import br.com.qsoft.governance.aigateway.application.CreateProvider;
import br.com.qsoft.governance.aigateway.application.CreateProviderCommand;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.aigateway.domain.provider.ProviderKind;
import br.com.qsoft.governance.aigateway.infrastructure.provider.StubProviderClient;
import br.com.qsoft.governance.agentpolicies.application.KnowledgeHit;
import br.com.qsoft.governance.agentpolicies.application.port.KnowledgeSeederRepository;
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

/** Agente de políticas (SPEC-05 §3/§4/§6): fluxo, ferramentas, isolamento e erros. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = GovernanceApplication.class)
class PolicyAgentIT {

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
    private static final String SECRET_B = "SEGREDO-DEPTO-B não pode vazar.";

    @LocalServerPort
    int port;

    @Autowired
    TestTenants testTenants;

    @Autowired
    CreateProvider createProvider;

    @Autowired
    CreateModel createModel;

    @Autowired
    KnowledgeSeederRepository seeder;

    @Autowired
    StubProviderClient stub;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private RestClient client;
    private Tenant tenant;
    private String adminEmail;
    private String managerEmail;
    private String otherDeptEmail;
    private Model echoModel;
    private Model toolModel;
    private Model errorModel;
    private Model timeoutModel;
    private Model forbiddenModel;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        String slug = "agent" + UUID.randomUUID().toString().substring(0, 8).toLowerCase();
        adminEmail = "admin@" + slug + ".com";
        managerEmail = "manager@" + slug + ".com";
        otherDeptEmail = "other@" + slug + ".com";
        tenant = testTenants.ensureTenant(slug, "Agent Demo", "Admin", adminEmail, PASSWORD);
        User admin = testTenants.admin(tenant, adminEmail);
        UUID departmentA = createDepartment();
        UUID departmentB = UUID.randomUUID();
        UUID departmentC = UUID.randomUUID();
        testTenants.ensureMember(tenant, admin.id(), managerEmail, "Manager", PASSWORD,
            HierarchyLevel.MANAGER, departmentA, "MANAGER");
        testTenants.ensureMember(tenant, admin.id(), otherDeptEmail, "Other", PASSWORD,
            HierarchyLevel.COLLABORATOR, departmentC, "COLLABORATOR");

        UUID providerId = createProvider.create(new CreateProviderCommand(
            tenant.id(), "Stub " + UUID.randomUUID(), ProviderKind.STUB, null, null, null)).id();
        echoModel = model(providerId, "stub-echo");
        toolModel = model(providerId, "stub-tool");
        errorModel = model(providerId, "stub-error");
        timeoutModel = model(providerId, "stub-timeout");
        forbiddenModel = model(providerId, "stub-echo");

        assertThat(putStatus("/api/v1/ai-gateway/departments/" + departmentA + "/model-policy",
            Map.of("allowedModelIds", List.of(echoModel.id().toString(),
                    toolModel.id().toString(), errorModel.id().toString(),
                    timeoutModel.id().toString()),
                "defaultModelId", echoModel.id().toString()))).isEqualTo(204);

        seeder.seed(tenant.id(), "Política de Viagens", true, Set.of(),
            List.of("A política de viagens permite deslocamentos aprovados pelo gestor."));
        seeder.seed(tenant.id(), "Política de Compras", false, Set.of(departmentA),
            List.of("A política de compras do departamento A exige três cotações."));
        seeder.seed(tenant.id(), "Política Secreta B", false, Set.of(departmentB),
            List.of(SECRET_B));
        stub.reset();
    }

    @Test
    void fluxoCompletoComFontesERecibo() {
        Map<String, Object> response = ask(managerEmail, "política de viagens", null);

        assertThat(response.get("resposta")).isNotNull();
        Map<?, ?> receipt = (Map<?, ?>) response.get("recibo");
        String requestId = (String) receipt.get("requestId");
        assertThat(requestId).isNotBlank();

        Integer attempts = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM aigateway_request_attempts WHERE request_id = ? "
                + "AND execution_status = 'SUCCEEDED'", Integer.class,
            UUID.fromString(requestId));
        assertThat(attempts).isEqualTo(1);
        Integer usage = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM observability_usage_records WHERE request_id = ?",
            Integer.class, UUID.fromString(requestId));
        assertThat(usage).isEqualTo(1);

        List<?> sources = (List<?>) response.get("fontes");
        assertThat(sources).isNotEmpty();
        assertThat(sources.toString()).doesNotContain(SECRET_B);

        String prompt = stub.lastCall().messages().toString();
        assertThat(prompt).doesNotContain(SECRET_B);
        assertThat(prompt).contains("Política de Viagens");
    }

    @Test
    void semCorrespondenciaNaoChamaOLlmNemCriaTentativa() {
        long before = attemptsForTenant();
        Map<String, Object> response = ask(managerEmail, "zebraquark", null);

        assertThat((String) response.get("resposta")).contains("Não encontrei");
        assertThat(response.get("recibo")).isNull();
        assertThat(attemptsForTenant()).isEqualTo(before);
    }

    @Test
    void executaFerramentaDaAllowlist() {
        Map<String, Object> response = ask(managerEmail, "política de viagens",
            toolModel.id().toString());

        assertThat((List<?>) response.get("ferramentasUsadas"))
            .extracting(Object::toString)
            .contains("buscar_politicas");
        assertThat(stub.lastCall().messages())
            .anySatisfy(message -> assertThat(message.role()).isEqualTo("TOOL"));
    }

    @Test
    void errosDaCompletionSaoMapeados() {
        assertThat(askStatus(managerEmail, "política", errorModel.id().toString())).isEqualTo(502);
        assertThat(askStatus(managerEmail, "política", timeoutModel.id().toString())).isEqualTo(504);
        assertThat(askStatus(managerEmail, "política", forbiddenModel.id().toString()))
            .isEqualTo(403);
        assertThat(askStatus(otherDeptEmail, "política", null)).isEqualTo(409);
    }

    @Test
    void perguntaNuncaVaiParaLog() {
        String question = "pergunta-secreta-" + UUID.randomUUID() + " política";
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            ask(managerEmail, question, null);
        } finally {
            root.detachAppender(appender);
        }

        assertThat(appender.list)
            .noneMatch(event -> event.getFormattedMessage().contains(question));
    }

    @Test
    void perguntaVaziaRetorna400() {
        assertThat(askStatus(managerEmail, "", null)).isEqualTo(400);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Model model(UUID providerId, String externalName) {
        return createModel.create(new CreateModelCommand(tenant.id(), providerId,
            "model " + UUID.randomUUID(), externalName, new BigDecimal("0.01"),
            new BigDecimal("0.03")));
    }

    private UUID createDepartment() {
        Map<String, Object> response = post(adminEmail, "/api/v1/departamentos",
            Map.of("name", "Agent Dept " + UUID.randomUUID()));
        return UUID.fromString((String) response.get("id"));
    }

    private long attemptsForTenant() {
        Long count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM aigateway_request_attempts WHERE tenant_id = ?",
            Long.class, tenant.id().value());
        return count == null ? 0 : count;
    }

    private Map<String, Object> ask(String email, String question, String requestedModelId) {
        Map<String, Object> body = requestedModelId == null
            ? Map.of("pergunta", question)
            : Map.of("pergunta", question, "requestedModelId", requestedModelId);
        return post(email, "/api/v1/agente-politicas/perguntas", body);
    }

    private int askStatus(String email, String question, String requestedModelId) {
        try {
            ask(email, question, requestedModelId);
            return 200;
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value();
        }
    }

    private Map<String, Object> post(String email, String uri, Object body) {
        return client.post().uri(uri)
            .header("Authorization", "Bearer " + login(email))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve().body(new ParameterizedTypeReference<>() {
            });
    }

    private int putStatus(String uri, Object body) {
        try {
            client.put().uri(uri)
                .header("Authorization", "Bearer " + login(adminEmail))
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
