package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
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
 * Teste de integração do módulo {@code department}: CRUD ponta a ponta,
 * isolamento de tenant, autorização (RBAC via ADR-34) e regras de conflito.
 * Exige Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = br.com.qsoft.governance.GovernanceApplication.class)
class DepartmentManagementIT {

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

    private static final String TENANT_SLUG = "deptdemo";
    private static final String OTHER_SLUG = "deptother";
    private static final String ADMIN_EMAIL = "admin@deptdemo.com";
    private static final String ADMIN_PASSWORD = "secret123";

    @LocalServerPort
    int port;

    @Autowired
    TestTenants testTenants;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        Tenant tenant = testTenants.ensureTenant(
            TENANT_SLUG, "Dept Demo", "Admin", ADMIN_EMAIL, ADMIN_PASSWORD);
        testTenants.ensureTenant(OTHER_SLUG, "Dept Other", "Admin", "admin@deptother.com", ADMIN_PASSWORD);
        testTenants.ensureMember(tenant, testTenants.admin(tenant, ADMIN_EMAIL).id(),
            "colab@deptdemo.com", "Colab", ADMIN_PASSWORD,
            HierarchyLevel.COLLABORATOR, null, "COLLABORATOR");
    }

    private String login(String email, String password) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", password))
            .retrieve().body(Map.class);
        return response == null ? null : (String) response.get("token");
    }

    private Map<?, ?> createDepartment(String token, String name) {
        return client.post().uri("/api/v1/departamentos")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("name", name, "description", "Time " + name,
                "monthlyBudget", "1500.00"))
            .retrieve().body(Map.class);
    }

    private List<Map<String, Object>> listDepartments(String token) {
        return client.get().uri("/api/v1/departamentos")
            .header("Authorization", "Bearer " + token)
            .retrieve().body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
            });
    }

    private int status(String method, String uri, String token, Map<String, Object> body) {
        try {
            RestClient.RequestBodySpec spec =
                client.method(HttpMethod.valueOf(method)).uri(uri);
            if (token != null) {
                spec.header("Authorization", "Bearer " + token);
            }
            if (body != null) {
                spec.contentType(MediaType.APPLICATION_JSON);
                spec.body(body);
            }
            return spec.retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException e) {
            return e.getStatusCode().value();
        }
    }

    @Test
    void shouldCreateListUpdateAndDeleteDepartment() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String name = "Marketing " + UUID.randomUUID();

        Map<?, ?> created = createDepartment(adminToken, name);
        assertThat(created.get("id")).isNotNull();
        assertThat(created.get("name")).isEqualTo(name);
        assertThat(created.get("status")).isEqualTo("ACTIVE");
        String id = (String) created.get("id");

        assertThat(listDepartments(adminToken)).anySatisfy(department ->
            assertThat(department.get("id")).isEqualTo(id));

        Map<?, ?> updated = client.put().uri("/api/v1/departamentos/" + id)
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("name", name, "description", "Atualizado", "monthlyBudget", "2000.00"))
            .retrieve().body(Map.class);
        assertThat(updated.get("description")).isEqualTo("Atualizado");

        assertThat(status("DELETE", "/api/v1/departamentos/" + id, adminToken, null)).isEqualTo(204);

        // Remoção é lógica: sai da listagem padrão (ACTIVE), permanece em INACTIVE.
        assertThat(listDepartments(adminToken)).noneSatisfy(department ->
            assertThat(department.get("id")).isEqualTo(id));
        List<Map<String, Object>> inactive = client.get()
            .uri("/api/v1/departamentos?status=INACTIVE")
            .header("Authorization", "Bearer " + adminToken)
            .retrieve().body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
            });
        assertThat(inactive).anySatisfy(department -> assertThat(department.get("id")).isEqualTo(id));
    }

    @Test
    void shouldRejectDuplicateNameInSameTenant() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String name = "Duplicado " + UUID.randomUUID();
        createDepartment(adminToken, name);

        assertThat(status("POST", "/api/v1/departamentos", adminToken,
            Map.of("name", name))).isEqualTo(409);
    }

    @Test
    void shouldNotSeeDepartmentsOfAnotherTenant() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String otherToken = login("admin@deptother.com", ADMIN_PASSWORD);
        String name = "SomenteOutro " + UUID.randomUUID();
        createDepartment(otherToken, name);

        assertThat(listDepartments(adminToken)).noneSatisfy(department ->
            assertThat(department.get("name")).isEqualTo(name));
    }

    @Test
    void shouldRejectCrossTenantDepartmentAccess() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String otherToken = login("admin@deptother.com", ADMIN_PASSWORD);
        String id = (String) createDepartment(otherToken, "Outro " + UUID.randomUUID()).get("id");

        assertThat(status("GET", "/api/v1/departamentos/" + id, adminToken, null)).isEqualTo(404);
    }

    @Test
    void shouldRejectWithoutAuthentication() {
        assertThat(status("GET", "/api/v1/departamentos", null, null)).isEqualTo(401);
        assertThat(status("POST", "/api/v1/departamentos", null, Map.of("name", "X"))).isEqualTo(401);
    }

    @Test
    void shouldRejectWithoutDepartmentManagePermission() {
        String collaboratorToken = login("colab@deptdemo.com", ADMIN_PASSWORD);

        // COLLABORATOR não tem DEPARTMENT:MANAGE (nem READ) na matriz padrão.
        assertThat(status("POST", "/api/v1/departamentos", collaboratorToken,
            Map.of("name", "Sem Permissao"))).isEqualTo(403);
        assertThat(status("GET", "/api/v1/departamentos", collaboratorToken, null)).isEqualTo(403);
    }
}
