package br.com.qsoft.governance.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * ITs de papéis e concessões (SPI-47): AZ04 (todas as concessões revogadas → 403),
 * AZ11 (revogação vale na requisição seguinte com o mesmo JWT) e AZ15 (último
 * TENANT_ADMIN → 409), além das guardas de atribuição. Exige Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = br.com.qsoft.governance.GovernanceApplication.class,
    properties = "app.bootstrap.demo-data=false")
class RoleGrantIT {

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

    private static final String SLUG = "rolegrantdemo";
    private static final String ADMIN_EMAIL = "admin@rolegrantdemo.com";
    private static final String MANAGER_EMAIL = "manager@rolegrantdemo.com";
    private static final String PASSWORD = "secret123";
    private static final UUID DEPT_A = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @LocalServerPort
    int port;

    @Autowired
    TestTenants testTenants;

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    RoleAssignmentRepository roleAssignmentRepository;

    private RestClient client;
    private Tenant tenant;
    private UserId adminId;
    private UserId managerId;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        tenant = testTenants.ensureTenant(SLUG, "Role Grant", "Admin", ADMIN_EMAIL, PASSWORD);
        adminId = testTenants.admin(tenant, ADMIN_EMAIL).id();
        User manager = testTenants.ensureMember(tenant, adminId, MANAGER_EMAIL, "Manager",
            PASSWORD, HierarchyLevel.MANAGER, DEPT_A, "MANAGER");
        managerId = manager.id();
    }

    private String login(String email) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", PASSWORD))
            .retrieve().body(Map.class);
        return response == null ? null : (String) response.get("token");
    }

    private int status(HttpMethod method, String uri, String token, Object body) {
        RestClient.RequestBodySpec spec = client.method(method).uri(uri);
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

    private UUID grantIdOf(UserId userId) {
        return roleAssignmentRepository.findActiveByTenantIdAndUserId(tenant.id(), userId)
            .stream().findFirst().orElseThrow().id().value();
    }

    @Test
    void az04RevokedUserIsForbiddenOnAnnotatedEndpoints() {
        String managerToken = login(MANAGER_EMAIL);
        // Antes da revogação, o MANAGER passa nos gates BY_TARGET (ROLE:READ / INVITE:MANAGE).
        assertThat(status(HttpMethod.GET, "/api/v1/papeis", managerToken, null)).isEqualTo(200);
        assertThat(status(HttpMethod.GET, "/api/v1/convites", managerToken, null)).isEqualTo(200);

        String adminToken = login(ADMIN_EMAIL);
        assertThat(status(HttpMethod.DELETE,
            "/api/v1/usuarios/" + managerId.value() + "/concessoes/" + grantIdOf(managerId),
            adminToken, null)).isEqualTo(204);

        assertThat(status(HttpMethod.GET, "/api/v1/papeis", managerToken, null)).isEqualTo(403);
        assertThat(status(HttpMethod.GET, "/api/v1/convites", managerToken, null)).isEqualTo(403);
    }

    @Test
    void az11RevocationTakesEffectOnTheNextRequestWithTheSameJwt() {
        String managerToken = login(MANAGER_EMAIL);
        assertThat(status(HttpMethod.GET, "/api/v1/papeis", managerToken, null)).isEqualTo(200);

        String adminToken = login(ADMIN_EMAIL);
        status(HttpMethod.DELETE,
            "/api/v1/usuarios/" + managerId.value() + "/concessoes/" + grantIdOf(managerId),
            adminToken, null);

        // Mesmo JWT, requisição seguinte: 403 (concessões lidas do banco).
        assertThat(status(HttpMethod.GET, "/api/v1/papeis", managerToken, null)).isEqualTo(403);
    }

    @Test
    void az15LastTenantAdminCannotBeRevokedAndGuardsAreEnforced() {
        String adminToken = login(ADMIN_EMAIL);

        // Último TENANT_ADMIN ativo → 409.
        assertThat(status(HttpMethod.DELETE,
            "/api/v1/usuarios/" + adminId.value() + "/concessoes/" + grantIdOf(adminId),
            adminToken, null)).isEqualTo(409);

        // Ator = alvo (com outra concessão não-admin) → 403.
        assertThat(status(HttpMethod.POST,
            "/api/v1/usuarios/" + adminId.value() + "/concessoes", adminToken,
            Map.of("roleKey", "COLLABORATOR"))).isEqualTo(403);

        // Concessão ativa idêntica → 409.
        assertThat(status(HttpMethod.POST,
            "/api/v1/usuarios/" + managerId.value() + "/concessoes", adminToken,
            Map.of("roleKey", "MANAGER", "departmentId", DEPT_A.toString()))).isEqualTo(409);
    }
}
