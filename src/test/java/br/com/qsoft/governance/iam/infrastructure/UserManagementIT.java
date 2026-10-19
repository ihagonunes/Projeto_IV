package br.com.qsoft.governance.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.support.TestTenants;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Teste de integração da gestão de usuários e convites (POPROJ-14):
 * listagem/busca paginada (SPI-62), edição de papel/departamento (SPI-63),
 * reenvio (SPI-59) e revogação de convite. Exige Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = br.com.qsoft.governance.GovernanceApplication.class)
class UserManagementIT {

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

    private static final String TENANT_SLUG = "mgmtdemo";
    private static final String OTHER_SLUG = "otherdemo";
    private static final String ADMIN_EMAIL = "admin@mgmtdemo.com";
    private static final String ADMIN_PASSWORD = "secret123";

    @LocalServerPort
    int port;

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    TestTenants testTenants;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        Tenant tenant = testTenants.ensureTenant(
            TENANT_SLUG, "Mgmt Demo", "Admin", ADMIN_EMAIL, ADMIN_PASSWORD);
        Tenant other = testTenants.ensureTenant(
            OTHER_SLUG, "Other Demo", "Admin", "admin@otherdemo.com", ADMIN_PASSWORD);
        testTenants.ensureMember(tenant, testTenants.admin(tenant, ADMIN_EMAIL).id(),
            "colab@mgmtdemo.com", "Colab", ADMIN_PASSWORD,
            HierarchyLevel.COLLABORATOR, null, "COLLABORATOR");
        testTenants.ensureMember(other, testTenants.admin(other, "admin@otherdemo.com").id(),
            "foreign@otherdemo.com", "Foreign", ADMIN_PASSWORD,
            HierarchyLevel.COLLABORATOR, null, "COLLABORATOR");
    }

    private String login(String email, String password) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", password))
            .retrieve().body(Map.class);
        return response == null ? null : (String) response.get("token");
    }

    private Map<?, ?> getJson(String token, String uri) {
        return client.get().uri(uri)
            .header("Authorization", "Bearer " + token)
            .retrieve().body(Map.class);
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

    private int loginStatus(String email, String password) {
        try {
            client.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .retrieve().toBodilessEntity();
            return 200;
        } catch (RestClientResponseException e) {
            return e.getStatusCode().value();
        }
    }

    private Map<?, ?> changeStatus(String token, UUID userId, String status) {
        return client.patch().uri("/api/v1/usuarios/" + userId + "/status")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("status", status))
            .retrieve().body(Map.class);
    }

    // ---------------------------------------------------------------- SPI-62

    @Test
    void shouldListOnlyUsersOfAuthenticatedTenant() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        Map<?, ?> page = getJson(adminToken, "/api/v1/usuarios?page=0&size=50");

        assertThat(page.get("totalElements")).isNotNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
        assertThat(content).isNotEmpty();
        assertThat(content).allSatisfy(user ->
            assertThat((String) user.get("email")).endsWith("@mgmtdemo.com"));
        assertThat(content).noneSatisfy(user ->
            assertThat((String) user.get("email")).isEqualTo("foreign@otherdemo.com"));
    }

    @Test
    void shouldFilterUsersByHierarchyLevel() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        Map<?, ?> page = getJson(adminToken, "/api/v1/usuarios?hierarchyLevel=COLLABORATOR");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
        assertThat(content).isNotEmpty();
        assertThat(content).allSatisfy(user ->
            assertThat(user.get("hierarchyLevel")).isEqualTo("COLLABORATOR"));
    }

    @Test
    void shouldRejectListWithoutAuthentication() {
        try {
            client.get().uri("/api/v1/usuarios").retrieve().toBodilessEntity();
            throw new AssertionError("Esperado 401/403");
        } catch (RestClientResponseException e) {
            assertThat(e.getStatusCode().value()).isIn(401, 403);
        }
    }

    // ---------------------------------------------------------------- SPI-63

    @Test
    void shouldUpdateUserDepartment() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        Tenant tenant = tenantRepository.findBySlug(TENANT_SLUG).orElseThrow();
        User target = userRepository.findByTenantIdAndEmail(tenant.id(), ADMIN_EMAIL).orElseThrow();
        UUID departmentId = UUID.randomUUID();

        Map<?, ?> updated = client.patch().uri("/api/v1/usuarios/" + target.id().value())
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("departmentId", departmentId.toString()))
            .retrieve().body(Map.class);

        assertThat(updated).isNotNull();
        // O PATCH edita o Department; o papel/nível NÃO é alterado por aqui (ADR-33).
        assertThat(updated.get("departmentId")).isEqualTo(departmentId.toString());
    }

    @Test
    void shouldRejectUpdateOfUserFromAnotherTenant() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        Tenant other = tenantRepository.findBySlug(OTHER_SLUG).orElseThrow();
        User foreign = userRepository.findByTenantIdAndEmail(other.id(), "foreign@otherdemo.com")
            .orElseThrow();

        try {
            client.patch().uri("/api/v1/usuarios/" + foreign.id().value())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("departmentId", UUID.randomUUID().toString()))
                .retrieve().toBodilessEntity();
            throw new AssertionError("Esperado 403/404");
        } catch (RestClientResponseException e) {
            assertThat(e.getStatusCode().value()).isIn(403, 404);
        }
    }

    // ---------------------------------------------------------------- SPI-59

    @Test
    void shouldResendInviteInvalidatingPreviousToken() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        Map<?, ?> created = createInvite(adminToken, "resend@mgmtdemo.com");
        String inviteId = (String) created.get("id");
        String oldToken = (String) created.get("token");

        Map<?, ?> renewed = client.post().uri("/api/v1/convites/" + inviteId + "/reenviar")
            .header("Authorization", "Bearer " + adminToken)
            .retrieve().body(Map.class);

        assertThat(renewed).isNotNull();
        String newToken = (String) renewed.get("token");
        assertThat(newToken).isNotBlank().isNotEqualTo(oldToken);
        assertThat(renewed.get("status")).isEqualTo("PENDING");

        // O token anterior deixa de funcionar; o novo é aceito.
        assertThat(acceptStatus(oldToken, "senha123")).isEqualTo(400);
        assertThat(acceptStatus(newToken, "senha123")).isEqualTo(200);
    }

    @Test
    void shouldRevokeInvite() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        Map<?, ?> created = createInvite(adminToken, "revoke@mgmtdemo.com");
        String inviteId = (String) created.get("id");
        String inviteToken = (String) created.get("token");

        client.delete().uri("/api/v1/convites/" + inviteId)
            .header("Authorization", "Bearer " + adminToken)
            .retrieve().toBodilessEntity();

        assertThat(acceptStatus(inviteToken, "senha123")).isEqualTo(400);
    }

    // ---------------------------------------------------------------- SPI-61

    @Test
    void shouldDeactivateAndReactivateUser() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        Tenant tenant = tenantRepository.findBySlug(TENANT_SLUG).orElseThrow();
        User collaborator = userRepository
            .findByTenantIdAndEmail(tenant.id(), "colab@mgmtdemo.com").orElseThrow();
        assertThat(loginStatus("colab@mgmtdemo.com", ADMIN_PASSWORD)).isEqualTo(200);

        // Desativa: o usuário deixa de autenticar, mas o registro é preservado.
        Map<?, ?> deactivated = changeStatus(adminToken, collaborator.id().value(), "INACTIVE");
        assertThat(deactivated.get("status")).isEqualTo("INACTIVE");
        assertThat(userRepository.findByIdUnscoped(collaborator.id())).isPresent();
        assertThat(loginStatus("colab@mgmtdemo.com", ADMIN_PASSWORD)).isEqualTo(401);

        // Reativa: o acesso é restaurado.
        Map<?, ?> reactivated = changeStatus(adminToken, collaborator.id().value(), "ACTIVE");
        assertThat(reactivated.get("status")).isEqualTo("ACTIVE");
        assertThat(loginStatus("colab@mgmtdemo.com", ADMIN_PASSWORD)).isEqualTo(200);
    }

    @Test
    void shouldRejectStatusChangeOfUserFromAnotherTenant() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        Tenant other = tenantRepository.findBySlug(OTHER_SLUG).orElseThrow();
        User foreign = userRepository.findByTenantIdAndEmail(other.id(), "foreign@otherdemo.com")
            .orElseThrow();

        try {
            changeStatus(adminToken, foreign.id().value(), "INACTIVE");
            throw new AssertionError("Esperado 403/404");
        } catch (RestClientResponseException e) {
            assertThat(e.getStatusCode().value()).isIn(403, 404);
        }
    }

    // ---------------------------------------------------------------- convites (listagem)

    @Test
    void shouldListInvitesOfTenantWithoutExposingTokens() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String invitedEmail = "listado@mgmtdemo.com";
        createInvite(adminToken, invitedEmail);

        List<Map<String, Object>> invites = client.get().uri("/api/v1/convites")
            .header("Authorization", "Bearer " + adminToken)
            .retrieve().body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
            });

        assertThat(invites).isNotNull().isNotEmpty();
        assertThat(invites).anySatisfy(invite ->
            assertThat(invite.get("email")).isEqualTo(invitedEmail));
        // O token (credencial de aceite) nunca deve aparecer em listagem.
        assertThat(invites).allSatisfy(invite -> assertThat(invite).doesNotContainKey("token"));
    }

    @Test
    void shouldRejectInviteListWithoutAuthentication() {
        try {
            client.get().uri("/api/v1/convites").retrieve().toBodilessEntity();
            throw new AssertionError("Esperado 401");
        } catch (RestClientResponseException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(401);
        }
    }
}
