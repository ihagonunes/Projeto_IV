package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.iam.application.ProvisionUser;
import br.com.qsoft.governance.iam.application.ProvisionUserCommand;
import br.com.qsoft.governance.iam.application.RegisterTenant;
import br.com.qsoft.governance.iam.application.RegisterTenantCommand;
import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantSlugAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    RegisterTenant registerTenant;

    @Autowired
    ProvisionUser provisionUser;

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    RoleAssignmentRepository roleAssignmentRepository;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        ensureTenant(TENANT_SLUG, "Dept Demo", ADMIN_EMAIL);
        ensureTenant(OTHER_SLUG, "Dept Other", "admin@deptother.com");
        ensureUser(TENANT_SLUG, "colab@deptdemo.com");
    }

    private void ensureTenant(String slug, String name, String adminEmail) {
        try {
            registerTenant.register(new RegisterTenantCommand(slug, name, slug + ".com"));
        } catch (TenantSlugAlreadyExistsException ignored) {
            // já criado por um teste anterior
        }
        Tenant tenant = tenantRepository.findBySlug(slug).orElseThrow();
        User admin = provisionIfAbsent(tenant, adminEmail);

        // EXECUTIVE contém DEPARTMENT:READ e DEPARTMENT:MANAGE (matriz padrão).
        Role executive = roleRepository
            .findByTenantIdAndName(tenant.id(), HierarchyLevel.EXECUTIVE.name())
            .orElseThrow();
        boolean assigned = roleAssignmentRepository
            .findByTenantIdAndUserId(tenant.id(), admin.id())
            .stream()
            .anyMatch(assignment -> assignment.roleId().equals(executive.id()));
        if (!assigned) {
            roleAssignmentRepository.save(
                RoleAssignment.create(admin.id(), executive.id(), tenant.id(), null));
        }
    }

    private void ensureUser(String slug, String email) {
        Tenant tenant = tenantRepository.findBySlug(slug).orElseThrow();
        provisionIfAbsent(tenant, email);
    }

    private User provisionIfAbsent(Tenant tenant, String email) {
        try {
            return provisionUser.provision(new ProvisionUserCommand(
                tenant.id(), email, "User", HierarchyLevel.COLLABORATOR,
                ADMIN_PASSWORD, null, Set.of()));
        } catch (UserEmailAlreadyExistsException ignored) {
            return userRepository.findByTenantIdAndEmail(tenant.id(), email).orElseThrow();
        }
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
