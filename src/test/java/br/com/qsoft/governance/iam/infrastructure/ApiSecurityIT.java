package br.com.qsoft.governance.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.support.TestTenants;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Map;
import java.util.UUID;
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
 * Testes de segurança da camada HTTP (servidor real) com PostgreSQL real:
 * 401 sem token, 403 sem USER:READ e login por derivação de tenant (sem
 * tenantSlug). Exige Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = br.com.qsoft.governance.GovernanceApplication.class)
class ApiSecurityIT {

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

    private static final String TENANT_SLUG = "secdemo";
    private static final String ADMIN_EMAIL = "admin@secdemo.com";
    private static final String USER_EMAIL = "user@secdemo.com";
    private static final String PASSWORD = "secret123";

    @LocalServerPort
    int port;

    @Autowired
    TestTenants testTenants;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        Tenant tenant = testTenants.ensureTenant(
            TENANT_SLUG, "Sec Demo", "Admin", ADMIN_EMAIL, PASSWORD);
        testTenants.ensureMember(tenant, testTenants.admin(tenant, ADMIN_EMAIL).id(),
            USER_EMAIL, "User", PASSWORD, HierarchyLevel.COLLABORATOR, null, "COLLABORATOR");
    }

    private String login(String email) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", PASSWORD))
            .retrieve().body(Map.class);
        return response == null ? null : (String) response.get("token");
    }

    private int listUsersStatus(String token) {
        var spec = client.get().uri("/api/v1/usuarios");
        if (token != null) {
            spec = spec.header("Authorization", "Bearer " + token);
        }
        try {
            return spec.retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException e) {
            return e.getStatusCode().value();
        }
    }

    @Test
    void shouldRejectListUsersWithoutToken() {
        assertThat(listUsersStatus(null)).isEqualTo(401);
    }

    @Test
    void shouldRejectListUsersWithoutUserReadPermission() {
        String token = login(USER_EMAIL);
        assertThat(listUsersStatus(token)).isEqualTo(403);
    }

    @Test
    void shouldListUsersWithUserReadPermission() {
        String token = login(ADMIN_EMAIL);
        assertThat(listUsersStatus(token)).isEqualTo(200);
    }

    @Test
    void shouldLoginWithoutTenantSlug() {
        assertThat(login(ADMIN_EMAIL)).isNotBlank();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnEffectivePermissionsForCurrentUser() {
        String token = login(ADMIN_EMAIL);

        Map<?, ?> response = client.get().uri("/api/v1/usuarios/me/permissoes")
            .header("Authorization", "Bearer " + token)
            .retrieve().body(Map.class);

        assertThat(response).isNotNull();
        Iterable<String> permissions = (Iterable<String>) response.get("permissions");
        assertThat(permissions).contains("USER:MANAGE");
    }
}
