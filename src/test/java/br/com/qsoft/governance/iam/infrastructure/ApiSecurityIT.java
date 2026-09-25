package br.com.qsoft.governance.iam.infrastructure;

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
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.iam.domain.rbac.Permission;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import java.util.Map;
import java.util.Set;
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
 * Testes de segurança da camada HTTP (servidor real) com PostgreSQL real.
 * Cobre: 401 sem token, 403 sem permissão USER:MANAGE, 201 com permissão e
 * login por derivação de tenant (sem tenantSlug). Exige Docker.
 *
 * A autorização do endpoint de criação é feita pelo PermissionInterceptor via
 * PolicyEvaluator (modelo RBAC granular — SPI-41).
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
    private static final String ROLE_NAME = "USER_MANAGER";

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
        ensureData();
    }

    private void ensureData() {
        try {
            registerTenant.register(new RegisterTenantCommand(TENANT_SLUG, "Sec Demo", TENANT_SLUG + ".com"));
        } catch (TenantSlugAlreadyExistsException ignored) {
            // já criado por um teste anterior
        }
        Tenant tenant = tenantRepository.findBySlug(TENANT_SLUG).orElseThrow();
        User admin = provisionIfAbsent(tenant, ADMIN_EMAIL, "Admin", HierarchyLevel.EXECUTIVE);
        provisionIfAbsent(tenant, USER_EMAIL, "User", HierarchyLevel.COLLABORATOR);

        // Papel granular com USER:MANAGE atribuído ao admin (modelo RBAC — SPI-41).
        Role role = roleRepository.findByTenantIdAndName(tenant.id(), ROLE_NAME)
            .orElseGet(() -> {
                Role created = Role.create(RoleId.random(), tenant.id(), ROLE_NAME);
                created.grantPermission(Permission.of(Resource.USER, Action.MANAGE));
                return roleRepository.save(created);
            });
        boolean alreadyAssigned = roleAssignmentRepository
            .findByTenantIdAndUserId(tenant.id(), admin.id())
            .stream()
            .anyMatch(assignment -> assignment.roleId().equals(role.id()));
        if (!alreadyAssigned) {
            roleAssignmentRepository.save(
                RoleAssignment.create(admin.id(), role.id(), tenant.id(), null));
        }
    }

    private User provisionIfAbsent(Tenant tenant, String email, String name, HierarchyLevel level) {
        try {
            return provisionUser.provision(new ProvisionUserCommand(
                tenant.id(), email, name, level, PASSWORD, null, Set.of()));
        } catch (UserEmailAlreadyExistsException ignored) {
            return userRepository.findByTenantIdAndEmail(tenant.id(), email).orElseThrow();
        }
    }

    private String login(String email) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", PASSWORD))
            .retrieve().body(Map.class);
        return response == null ? null : (String) response.get("token");
    }

    private int createUser(String token, String email) {
        var spec = client.post().uri("/api/v1/usuarios")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of(
                "email", email,
                "name", "Novo Usuário",
                "hierarchyLevel", "COLLABORATOR",
                "password", PASSWORD));
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
    void shouldRejectCreateUserWithoutToken() {
        assertThat(createUser(null, "sem-token@secdemo.com")).isEqualTo(401);
    }

    @Test
    void shouldRejectCreateUserWithoutManageUsersPermission() {
        String token = login(USER_EMAIL);
        assertThat(createUser(token, "sem-permissao@secdemo.com")).isEqualTo(403);
    }

    @Test
    void shouldCreateUserWithManageUsersPermission() {
        String token = login(ADMIN_EMAIL);
        assertThat(createUser(token, "com-permissao@secdemo.com")).isEqualTo(201);
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
