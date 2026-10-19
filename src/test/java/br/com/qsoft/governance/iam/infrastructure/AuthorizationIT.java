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
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import java.util.Map;
import java.util.Set;
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
    private static final String PASSWORD = "secret123";

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
    private UUID targetId;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        ensureData();
    }

    private void ensureData() {
        try {
            registerTenant.register(new RegisterTenantCommand(TENANT_SLUG, "RBAC Demo", TENANT_SLUG + ".com"));
        } catch (TenantSlugAlreadyExistsException ignored) {
            // já criado por um teste anterior
        }
        Tenant tenant = tenantRepository.findBySlug(TENANT_SLUG).orElseThrow();
        User admin = provisionIfAbsent(tenant, ADMIN_EMAIL, HierarchyLevel.EXECUTIVE);
        provisionIfAbsent(tenant, USER_EMAIL, HierarchyLevel.COLLABORATOR);
        targetId = admin.id().value();

        // O papel de sistema EXECUTIVE contém USER:MANAGE (ADR-36).
        Role executive = roleRepository
            .findByTenantIdAndName(tenant.id(), HierarchyLevel.EXECUTIVE.name())
            .orElseThrow();
        boolean assigned = roleAssignmentRepository
            .findByTenantIdAndUserId(tenant.id(), admin.id())
            .stream()
            .anyMatch(assignment -> assignment.roleId().equals(executive.id()));
        if (!assigned) {
            roleAssignmentRepository.save(RoleAssignment.grant(
                admin.id(), executive, null,
                br.com.qsoft.governance.iam.domain.rbac.GrantOrigin.MIGRATION, null,
                java.time.Instant.now()));
        }
    }

    private User provisionIfAbsent(Tenant tenant, String email, HierarchyLevel level) {
        try {
            return provisionUser.provision(new ProvisionUserCommand(
                tenant.id(), email, email, level, PASSWORD, null, Set.of()));
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
}
