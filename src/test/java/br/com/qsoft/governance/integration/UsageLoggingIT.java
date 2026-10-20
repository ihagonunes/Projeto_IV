package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.aigateway.application.CreateModel;
import br.com.qsoft.governance.aigateway.application.CreateModelCommand;
import br.com.qsoft.governance.aigateway.application.CreateProvider;
import br.com.qsoft.governance.aigateway.application.CreateProviderCommand;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.aigateway.domain.provider.ProviderKind;
import br.com.qsoft.governance.sharedkernel.event.AIRequestCompleted;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.support.TestTenants;
import br.com.qsoft.governance.observability.application.port.UsageRecordRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Teste de integração do pipeline de logging (SPI-78):
 * completion → AIRequestCompleted → observability → UsageRecord → API.
 * Exige Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = br.com.qsoft.governance.GovernanceApplication.class)
class UsageLoggingIT {

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

    private static final String TENANT_SLUG = "usagedemo";
    private static final String OTHER_SLUG = "usageother";
    private static final String ADMIN_EMAIL = "admin@usagedemo.com";
    private static final String COLLAB_EMAIL = "colab@usagedemo.com";
    private static final String ADMIN_PASSWORD = "secret123";

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
    ApplicationEventPublisher eventPublisher;

    @Autowired
    UsageRecordRepository usageRecordRepository;

    @Autowired
    TestTenants testTenants;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        Tenant tenant = testTenants.ensureTenant(
            TENANT_SLUG, "Usage Demo", "Admin", ADMIN_EMAIL, ADMIN_PASSWORD);
        testTenants.ensureTenant(OTHER_SLUG, "Usage Other", "Admin", "admin@usageother.com", ADMIN_PASSWORD);
        testTenants.ensureMember(tenant, testTenants.admin(tenant, ADMIN_EMAIL).id(), COLLAB_EMAIL,
            "Colab", ADMIN_PASSWORD, HierarchyLevel.COLLABORATOR, null, "COLLABORATOR");
    }

    private String login(String email, String password) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", password))
            .retrieve().body(Map.class);
        return response == null ? null : (String) response.get("token");
    }

    /** "Completion" do Gateway: cria o modelo com preço e emite AIRequestCompleted. */
    private UUID complete(Tenant tenant, UUID requestId) {
        return complete(tenant, requestId, UUID.randomUUID());
    }

    private UUID complete(Tenant tenant, UUID requestId, UUID userId) {
        UUID providerId = createProvider.create(new CreateProviderCommand(
            tenant.id(), "OpenAI " + UUID.randomUUID(), ProviderKind.STUB, null, null, null)).id();
        Model model = createModel.create(new CreateModelCommand(
            tenant.id(), providerId, "gpt-4 " + UUID.randomUUID(), null,
            new BigDecimal("0.010000"), new BigDecimal("0.030000")));

        eventPublisher.publishEvent(new AIRequestCompleted(
            requestId, tenant.id().value(), null, userId, model.id(), providerId,
            1000, 2000, model.estimatedCost(1000, 2000), 150, "COMPLETED"));
        return model.id();
    }

    private int usageStatus(String token) {
        return usageStatusQuery(token, "");
    }

    private int usageStatusQuery(String token, String query) {
        try {
            var spec = client.get().uri("/api/v1/observability/usage" + query);
            if (token != null) {
                spec.header("Authorization", "Bearer " + token);
            }
            spec.retrieve().toBodilessEntity();
            return 200;
        } catch (RestClientResponseException e) {
            return e.getStatusCode().value();
        }
    }

    @Test
    void shouldPersistUsageRecordFromCompletionAndExposeItInTheApi() {
        Tenant tenant = tenantRepository.findBySlug(TENANT_SLUG).orElseThrow();
        UUID requestId = UUID.randomUUID();

        complete(tenant, requestId);

        assertThat(usageRecordRepository.existsByRequestId(requestId)).isTrue();

        Map<?, ?> page = client.get().uri("/api/v1/observability/usage")
            .header("Authorization", "Bearer " + login(ADMIN_EMAIL, ADMIN_PASSWORD))
            .retrieve().body(Map.class);
        assertThat(page.get("totalElements")).isNotNull();
        assertThat(page.toString()).contains(requestId.toString());
    }

    @Test
    void shouldBeIdempotentByRequestId() {
        Tenant tenant = tenantRepository.findBySlug(TENANT_SLUG).orElseThrow();
        UUID requestId = UUID.randomUUID();
        UUID modelId = complete(tenant, requestId);

        eventPublisher.publishEvent(new AIRequestCompleted(
            requestId, tenant.id().value(), null, UUID.randomUUID(), modelId, UUID.randomUUID(),
            1000, 2000, new BigDecimal("0.070000"), 150, "COMPLETED"));

        Map<?, ?> page = client.get().uri("/api/v1/observability/usage?size=100")
            .header("Authorization", "Bearer " + login(ADMIN_EMAIL, ADMIN_PASSWORD))
            .retrieve().body(Map.class);
        long occurrences = page.toString().split(requestId.toString(), -1).length - 1;
        assertThat(occurrences).isEqualTo(1);
    }

    @Test
    void shouldIsolateUsageByTenant() {
        Tenant tenant = tenantRepository.findBySlug(TENANT_SLUG).orElseThrow();
        UUID requestId = UUID.randomUUID();
        complete(tenant, requestId);

        Map<?, ?> page = client.get().uri("/api/v1/observability/usage?size=100")
            .header("Authorization", "Bearer " + login("admin@usageother.com", ADMIN_PASSWORD))
            .retrieve().body(Map.class);
        assertThat(page.toString()).doesNotContain(requestId.toString());
    }

    @Test
    void shouldRejectUsageQueryWithoutAuthentication() {
        assertThat(usageStatus(null)).isEqualTo(401);
    }

    /**
     * AZ20: COLLABORATOR só vê o próprio uso e não pode filtrar por outro usuário.
     */
    @Test
    void az20CollaboratorOnlySeesOwnUsageAndCannotFilterAnotherUser() {
        Tenant tenant = tenantRepository.findBySlug(TENANT_SLUG).orElseThrow();
        var admin = testTenants.admin(tenant, ADMIN_EMAIL);
        var collaborator = userRepository.findByTenantIdAndEmail(tenant.id(), COLLAB_EMAIL).orElseThrow();

        UUID adminRequest = UUID.randomUUID();
        UUID collabRequest = UUID.randomUUID();
        complete(tenant, adminRequest, admin.id().value());
        complete(tenant, collabRequest, collaborator.id().value());

        String token = login(COLLAB_EMAIL, ADMIN_PASSWORD);

        // userId de outro → 403 (filtro fora do escopo).
        assertThat(usageStatusQuery(token, "?userId=" + admin.id().value())).isEqualTo(403);

        // Sem filtro → apenas os próprios registros.
        Map<?, ?> page = client.get().uri("/api/v1/observability/usage?size=100")
            .header("Authorization", "Bearer " + token)
            .retrieve().body(Map.class);
        assertThat(page.toString())
            .contains(collabRequest.toString())
            .doesNotContain(adminRequest.toString());
    }
}
