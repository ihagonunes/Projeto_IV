package br.com.qsoft.governance.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * Fluxo de produção ponta a ponta, SEM seed de demonstração e SEM inserir
 * {@code RoleAssignment} à mão (ADR-37, AZ01/AZ02/AZ03): registrar tenant →
 * o registrante vira TENANT_ADMIN → convida → convidado aceita → login →
 * acessa somente o que a concessão permite. Exige Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = br.com.qsoft.governance.GovernanceApplication.class,
    properties = "app.bootstrap.demo-data=false")
class OnboardingFlowIT {

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

    private static final String ADMIN_PASSWORD = "admin-secret-123";
    private static final String MEMBER_PASSWORD = "member-secret-123";

    @LocalServerPort
    int port;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void registrantBecomesAdminInvitesCollaboratorAndCollaboratorSeesOnlyWhatTheGrantAllows() {
        String adminEmail = "dona@onboarding.com";
        registerTenant("onboarding", "onboarding.com", adminEmail);

        // AZ01: o registrante loga e administra o tenant.
        String adminToken = login(adminEmail, ADMIN_PASSWORD);
        assertThat(status("PUT", "/api/v1/tenants/me", adminToken, Map.of("name", "Onboarding SA")))
            .isEqualTo(200);

        // AZ02: o admin convida um COLLABORATOR; o convidado aceita e loga.
        String memberEmail = "colab@onboarding.com";
        Map<?, ?> invite = client.post().uri("/api/v1/convites")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", memberEmail, "roleKey", "COLLABORATOR"))
            .retrieve().body(Map.class);
        assertThat(invite).isNotNull();
        String inviteToken = (String) invite.get("token");
        assertThat(status("POST", "/api/v1/convites/aceitar", null,
            Map.of("token", inviteToken, "name", "Colab", "password", MEMBER_PASSWORD)))
            .isEqualTo(200);
        String memberToken = login(memberEmail, MEMBER_PASSWORD);

        // AZ03: o colaborador lê o próprio uso, mas não lista usuários nem administra o tenant.
        assertThat(status("GET", "/api/v1/observability/metrics/usage", memberToken, null))
            .isEqualTo(200);
        assertThat(status("GET", "/api/v1/usuarios", memberToken, null)).isEqualTo(403);
        assertThat(status("PUT", "/api/v1/tenants/me", memberToken, Map.of("name", "Hack")))
            .isEqualTo(403);
    }

    private void registerTenant(String slug, String domain, String adminEmail) {
        client.post().uri("/api/v1/tenants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of(
                "slug", slug,
                "name", "Onboarding",
                "emailDomain", domain,
                "adminName", "Dona",
                "adminEmail", adminEmail,
                "adminPassword", ADMIN_PASSWORD))
            .retrieve().toBodilessEntity();
    }

    private String login(String email, String password) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", password))
            .retrieve().body(Map.class);
        assertThat(response).isNotNull();
        return (String) response.get("token");
    }

    private int status(String method, String uri, String token, Object body) {
        RestClient.RequestBodySpec spec = client.method(org.springframework.http.HttpMethod.valueOf(method))
            .uri(uri);
        if (token != null) {
            spec = spec.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        try {
            return spec.retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException e) {
            return e.getStatusCode().value();
        }
    }
}
