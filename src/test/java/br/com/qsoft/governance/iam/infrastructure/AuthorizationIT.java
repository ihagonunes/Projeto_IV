package br.com.qsoft.governance.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.iam.support.TestTenants;
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
 * Teste de integração do RBAC no endpoint de modificação hierárquica
 * (POST /api/v1/usuarios/{id}/nivel) com servidor real e PostgreSQL.
 * Exige Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = br.com.qsoft.governance.GovernanceApplication.class)
class AuthorizationIT {

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

    private static final String TENANT_SLUG = "rbacdemo";
    private static final String ADMIN_EMAIL = "admin@rbacdemo.com";
    private static final String USER_EMAIL = "user@rbacdemo.com";
    private static final String MANAGER_EMAIL = "manager@rbacdemo.com";
    private static final String PASSWORD = "secret123";

    @LocalServerPort
    int port;

    @Autowired
    TestTenants testTenants;

    private RestClient client;
    private UUID targetId;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        Tenant tenant = testTenants.ensureTenant(
            TENANT_SLUG, "RBAC Demo", "Admin", ADMIN_EMAIL, PASSWORD);
        UserId adminId = testTenants.admin(tenant, ADMIN_EMAIL).id();
        targetId = adminId.value();

        testTenants.ensureMember(tenant, adminId, USER_EMAIL, "User", PASSWORD,
            HierarchyLevel.COLLABORATOR, null, "COLLABORATOR");

        // MANAGER com concessão DEPARTMENT e USER:MANAGE — cenário AZ19.
        testTenants.ensureMember(tenant, adminId, MANAGER_EMAIL, "Manager", PASSWORD,
            HierarchyLevel.MANAGER, UUID.randomUUID(), "MANAGER");
    }

    private String login(String email) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", PASSWORD))
            .retrieve().body(Map.class);
        return response == null ? null : (String) response.get("token");
    }

    private int changeLevel(String token, UUID id, String level) {
        var spec = client.post().uri("/api/v1/usuarios/" + id + "/nivel")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("hierarchyLevel", level));
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
    void shouldAllowAdminWithPermissionToChangeHierarchy() {
        String token = login(ADMIN_EMAIL);
        assertThat(changeLevel(token, targetId, "MANAGER")).isEqualTo(200);
    }

    @Test
    void shouldDenyUserWithoutPermissionToChangeHierarchy() {
        String token = login(USER_EMAIL);
        assertThat(changeLevel(token, targetId, "EXECUTIVE")).isEqualTo(403);
    }

    @Test
    void shouldRejectChangeWithoutToken() {
        assertThat(changeLevel(null, targetId, "EXECUTIVE")).isEqualTo(401);
    }

    /**
     * AZ19: endpoint TENANT_ONLY (USER:MANAGE) rejeita concessão DEPARTMENT.
     */
    @Test
    void az19DepartmentScopedGrantIsRejectedOnTenantOnlyEndpoint() {
        String token = login(MANAGER_EMAIL);
        assertThat(changeLevel(token, targetId, "LEADER")).isEqualTo(403);
    }
}
