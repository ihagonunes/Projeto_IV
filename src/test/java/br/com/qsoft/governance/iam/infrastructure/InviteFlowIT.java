package br.com.qsoft.governance.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.iam.support.TestTenants;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Teste de integração do fluxo de convites (SPI-29/SPI-30) com servidor real
 * e PostgreSQL: emitir convite (INVITE:MANAGE) -> aceitar (público, define senha)
 * -> login do novo usuário; e rejeição de reutilização. Exige Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = br.com.qsoft.governance.GovernanceApplication.class)
class InviteFlowIT {

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

    private static final String TENANT_SLUG = "invitedemo";
    private static final String ADMIN_EMAIL = "admin@invitedemo.com";
    private static final String ADMIN_PASSWORD = "secret123";

    @LocalServerPort
    int port;

    @Autowired
    TestTenants testTenants;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        testTenants.ensureTenant(TENANT_SLUG, "Invite Demo", "Admin", ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    private String login(String email, String password) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", password))
            .retrieve().body(Map.class);
        return response == null ? null : (String) response.get("token");
    }

    private Map<?, ?> createInvite(String token, String email) {
        return client.post().uri("/api/v1/convites")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "roleKey", "COLLABORATOR"))
            .retrieve().body(Map.class);
    }

    private int acceptStatus(String inviteToken, String password) {
        try {
            client.post().uri("/api/v1/convites/aceitar")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("token", inviteToken, "name", "Novo", "password", password))
                .retrieve().toBodilessEntity();
            return 200;
        } catch (RestClientResponseException e) {
            return e.getStatusCode().value();
        }
    }

    @Test
    void shouldCreateInviteAcceptAndLogin() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String invitedEmail = "novo@invitedemo.com";

        Map<?, ?> invite = createInvite(adminToken, invitedEmail);
        String inviteToken = (String) invite.get("token");
        assertThat(inviteToken).isNotBlank();
        assertThat(invite.get("status")).isEqualTo("PENDING");

        assertThat(acceptStatus(inviteToken, "senha123")).isEqualTo(200);

        // O novo usuário consegue logar com a senha definida.
        assertThat(login(invitedEmail, "senha123")).isNotBlank();
    }

    @Test
    void shouldRejectInviteReuse() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String inviteToken = (String) createInvite(adminToken, "reuse@invitedemo.com").get("token");

        assertThat(acceptStatus(inviteToken, "senha123")).isEqualTo(200);
        assertThat(acceptStatus(inviteToken, "outra123")).isEqualTo(400);
    }

    @Test
    void shouldRejectInviteWithoutPermission() {
        // Sem token: /api/v1/convites exige autenticação.
        try {
            client.post().uri("/api/v1/convites")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "x@invitedemo.com", "roleKey", "COLLABORATOR"))
                .retrieve().toBodilessEntity();
            throw new AssertionError("Esperado 401/403");
        } catch (RestClientResponseException e) {
            assertThat(e.getStatusCode().value()).isIn(401, 403);
        }
    }
}
