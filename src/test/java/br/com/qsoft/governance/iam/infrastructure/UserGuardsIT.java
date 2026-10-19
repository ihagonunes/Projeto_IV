package br.com.qsoft.governance.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.support.TestTenants;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.List;
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
 * ITs das guardas de gestão de usuários (SPEC-01 §4/§6/§11):
 * AZ05 (rótulo sem efeito), AZ06 (ator = alvo → 403), AZ08 (gestor não lê de
 * outro departamento), AZ09-HTTP (outro tenant → 404), AZ14 (mover para depto
 * fora do escopo → 403) e AZ16 (gestor não altera admin lotado no seu depto).
 * Exige Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = br.com.qsoft.governance.GovernanceApplication.class,
    properties = "app.bootstrap.demo-data=false")
class UserGuardsIT {

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

    private static final String SLUG = "userguardsdemo";
    private static final String OTHER_SLUG = "userguardsother";
    private static final String ADMIN_EMAIL = "admin@userguardsdemo.com";
    private static final String OTHER_ADMIN_EMAIL = "admin@userguardsother.com";
    private static final String MANAGER_EMAIL = "manager@userguardsdemo.com";
    private static final String LEADER_EMAIL = "leader@userguardsdemo.com";
    private static final String COLLAB_EMAIL = "collab@userguardsdemo.com";
    private static final String ADMIN_A_EMAIL = "admina@userguardsdemo.com";
    private static final String PASSWORD = "secret123";
    private static final UUID DEPT_A = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID DEPT_B = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @LocalServerPort
    int port;

    @Autowired
    TestTenants testTenants;

    private RestClient client;
    private Tenant tenant;
    private UserId adminId;
    private User manager;
    private User leader;
    private User collaborator;
    private User adminInA;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        tenant = testTenants.ensureTenant(SLUG, "Guards Demo", "Admin", ADMIN_EMAIL, PASSWORD);
        adminId = testTenants.admin(tenant, ADMIN_EMAIL).id();

        manager = testTenants.ensureMember(tenant, adminId, MANAGER_EMAIL, "Manager",
            PASSWORD, HierarchyLevel.MANAGER, DEPT_A, "MANAGER");
        leader = testTenants.ensureMember(tenant, adminId, LEADER_EMAIL, "Leader",
            PASSWORD, HierarchyLevel.LEADER, DEPT_A, "LEADER");
        collaborator = testTenants.ensureMember(tenant, adminId, COLLAB_EMAIL, "Collab",
            PASSWORD, HierarchyLevel.COLLABORATOR, DEPT_A, "COLLABORATOR");
        adminInA = testTenants.ensureMember(tenant, adminId, ADMIN_A_EMAIL, "Admin A",
            PASSWORD, HierarchyLevel.EXECUTIVE, DEPT_A, "TENANT_ADMIN");

        // Segundo tenant (para AZ09-HTTP).
        testTenants.ensureTenant(OTHER_SLUG, "Guards Other", "Admin", OTHER_ADMIN_EMAIL, PASSWORD);
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listUsers(String token) {
        Map<String, Object> page = client.get().uri("/api/v1/usuarios?size=100")
            .header("Authorization", "Bearer " + token)
            .retrieve().body(Map.class);
        return (List<Map<String, Object>>) page.get("content");
    }

    @Test
    void az05LabelHasNoAuthorizationEffect() {
        String adminToken = login(ADMIN_EMAIL);
        // Muda o RÓTULO do LEADER para EXECUTIVE (continua LEADER na concessão).
        assertThat(status(HttpMethod.POST,
            "/api/v1/usuarios/" + leader.id().value() + "/nivel", adminToken,
            Map.of("hierarchyLevel", "EXECUTIVE"))).isEqualTo(200);

        // A visibilidade continua a do departamento (LEADER/DEPARTMENT A).
        String leaderToken = login(LEADER_EMAIL);
        List<Map<String, Object>> users = listUsers(leaderToken);
        assertThat(users).isNotEmpty();
        assertThat(users).allSatisfy(user -> assertThat(user.get("tenantId")).isNotNull());
        long fromDeptB = users.stream()
            .filter(user -> DEPT_B.toString().equals(user.get("departmentId")))
            .count();
        assertThat(fromDeptB).isZero();
        // adminA (TENANT_ADMIN, lotado em A) aparece; quem está só em B não.
        assertThat(users).anySatisfy(user ->
            assertThat(user.get("email")).isEqualTo(ADMIN_A_EMAIL));
    }

    @Test
    void az06ManagerCannotAlterItself() {
        String managerToken = login(MANAGER_EMAIL);
        UUID id = manager.id().value();

        assertThat(status(HttpMethod.PATCH, "/api/v1/usuarios/" + id, managerToken,
            Map.of("departmentId", DEPT_B.toString()))).isEqualTo(403);
        assertThat(status(HttpMethod.PATCH, "/api/v1/usuarios/" + id + "/status", managerToken,
            Map.of("status", "INACTIVE"))).isEqualTo(403);
        assertThat(status(HttpMethod.POST, "/api/v1/usuarios/" + id + "/nivel", managerToken,
            Map.of("hierarchyLevel", "EXECUTIVE"))).isEqualTo(403);
    }

    @Test
    void az08ManagerDoesNotReadUsersFromAnotherDepartment() {
        String managerToken = login(MANAGER_EMAIL);

        // Usuário lotado só em B não é acessível pelo MANAGER(A).
        User leaderB = testTenants.ensureMember(tenant, adminId,
            "leaderb@userguardsdemo.com", "Leader B", PASSWORD,
            HierarchyLevel.LEADER, DEPT_B, "LEADER");
        assertThat(status(HttpMethod.GET,
            "/api/v1/usuarios/" + leaderB.id().value() + "/permissoes", managerToken, null))
            .isEqualTo(403);

        List<Map<String, Object>> users = listUsers(managerToken);
        assertThat(users).noneSatisfy(user ->
            assertThat(user.get("email")).isEqualTo("leaderb@userguardsdemo.com"));
    }

    @Test
    void az09CrossTenantIdsReturnNotFound() {
        String adminToken = login(ADMIN_EMAIL);
        Tenant other = testTenants.ensureTenant(OTHER_SLUG, "Guards Other",
            "Admin", OTHER_ADMIN_EMAIL, PASSWORD);
        User foreign = testTenants.ensureMember(other, testTenants.admin(other, OTHER_ADMIN_EMAIL).id(),
            "foreign@userguardsother.com", "Foreign", PASSWORD,
            HierarchyLevel.COLLABORATOR, null, "COLLABORATOR");

        assertThat(status(HttpMethod.GET,
            "/api/v1/usuarios/" + foreign.id().value() + "/permissoes", adminToken, null))
            .isEqualTo(404);
        assertThat(status(HttpMethod.PATCH,
            "/api/v1/usuarios/" + foreign.id().value(), adminToken,
            Map.of("departmentId", UUID.randomUUID().toString()))).isEqualTo(404);
        assertThat(status(HttpMethod.PATCH,
            "/api/v1/usuarios/" + foreign.id().value() + "/status", adminToken,
            Map.of("status", "INACTIVE"))).isEqualTo(404);
    }

    @Test
    void az14ManagerCannotMoveUserOutsideItsDepartment() {
        String managerToken = login(MANAGER_EMAIL);

        assertThat(status(HttpMethod.PATCH,
            "/api/v1/usuarios/" + collaborator.id().value(), managerToken,
            Map.of("departmentId", DEPT_B.toString()))).isEqualTo(403);
        // Movimentação dentro do escopo continua permitida.
        assertThat(status(HttpMethod.PATCH,
            "/api/v1/usuarios/" + collaborator.id().value(), managerToken,
            Map.of("departmentId", DEPT_A.toString()))).isEqualTo(200);
    }

    @Test
    void az16ManagerCannotAlterAnAdminInItsDepartment() {
        String managerToken = login(MANAGER_EMAIL);

        assertThat(status(HttpMethod.PATCH,
            "/api/v1/usuarios/" + adminInA.id().value() + "/status", managerToken,
            Map.of("status", "INACTIVE"))).isEqualTo(403);
        assertThat(status(HttpMethod.PATCH,
            "/api/v1/usuarios/" + adminInA.id().value(), managerToken,
            Map.of("departmentId", DEPT_A.toString()))).isEqualTo(403);
    }
}
