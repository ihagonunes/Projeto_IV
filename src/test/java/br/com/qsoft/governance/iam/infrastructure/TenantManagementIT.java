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
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
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
 * Teste de integração do cadastro do tenant (POPROJ-13):
 * SPI-52 (leitura/edição dos dados cadastrais) e SPI-57 (status ACTIVE/INACTIVE
 * bloqueia login e requests protegidos). Exige Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = br.com.qsoft.governance.GovernanceApplication.class)
class TenantManagementIT {

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

    private static final String TENANT_SLUG = "tenantmgmt";
    private static final String ADMIN_EMAIL = "admin@tenantmgmt.com";
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
        ensureTenant();
        ensureUser("colab@tenantmgmt.com");
    }

    private void ensureTenant() {
        try {
            registerTenant.register(new RegisterTenantCommand(TENANT_SLUG, "Tenant Mgmt", TENANT_SLUG + ".com"));
        } catch (TenantSlugAlreadyExistsException ignored) {
            // já criado por um teste anterior
        }
        Tenant tenant = tenantRepository.findBySlug(TENANT_SLUG).orElseThrow();

        // Garante o estado ACTIVE: um teste de SPI-57 suspende e reativa o tenant,
        // e o setUp não pode depender da ordem de execução dos testes.
        if (tenant.status() != TenantStatus.ACTIVE) {
            tenantRepository.save(tenant.activate());
            tenant = tenantRepository.findBySlug(TENANT_SLUG).orElseThrow();
        }

        User admin = provisionIfAbsent(tenant, ADMIN_EMAIL);

        // O papel TENANT_ADMIN contém TENANT:READ e TENANT:MANAGE (ADR-36).
        Role tenantAdmin = roleRepository
            .findByTenantIdAndName(tenant.id(), "TENANT_ADMIN")
            .orElseThrow();
        boolean assigned = roleAssignmentRepository
            .findByTenantIdAndUserId(tenant.id(), admin.id())
            .stream()
            .anyMatch(assignment -> assignment.roleId().equals(tenantAdmin.id()));
        if (!assigned) {
            roleAssignmentRepository.save(RoleAssignment.grant(admin.id(), tenantAdmin, null,
                    br.com.qsoft.governance.iam.domain.rbac.GrantOrigin.MIGRATION, null,
                    java.time.Instant.now()));
        }
    }

    private void ensureUser(String email) {
        Tenant tenant = tenantRepository.findBySlug(TENANT_SLUG).orElseThrow();
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

    private Map<?, ?> getMe(String token) {
        return client.get().uri("/api/v1/tenants/me")
            .header("Authorization", "Bearer " + token)
            .retrieve().body(Map.class);
    }

    private int protectedStatus(String token) {
        try {
            client.get().uri("/api/v1/tenants/me")
                .header("Authorization", "Bearer " + token)
                .retrieve().toBodilessEntity();
            return 200;
        } catch (RestClientResponseException e) {
            return e.getStatusCode().value();
        }
    }

    // ---------------------------------------------------------------- SPI-52

    @Test
    void shouldReturnCurrentTenant() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        Map<?, ?> tenant = getMe(adminToken);

        assertThat(tenant).isNotNull();
        assertThat(tenant.get("slug")).isEqualTo(TENANT_SLUG);
        assertThat(tenant.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void shouldRejectCurrentTenantWithoutAuthentication() {
        try {
            client.get().uri("/api/v1/tenants/me").retrieve().toBodilessEntity();
            throw new AssertionError("Esperado 401");
        } catch (RestClientResponseException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(401);
        }
    }

    @Test
    void shouldUpdateTenantName() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        Map<?, ?> updated = client.put().uri("/api/v1/tenants/me")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("name", "Tenant Mgmt Renomeado"))
            .retrieve().body(Map.class);

        assertThat(updated).isNotNull();
        assertThat(updated.get("name")).isEqualTo("Tenant Mgmt Renomeado");
        // O slug é imutável.
        assertThat(updated.get("slug")).isEqualTo(TENANT_SLUG);
    }

    @Test
    void shouldRejectUpdateWithoutTenantManagePermission() {
        String collaboratorToken = login("colab@tenantmgmt.com", ADMIN_PASSWORD);

        try {
            client.put().uri("/api/v1/tenants/me")
                .header("Authorization", "Bearer " + collaboratorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Nao Autorizado"))
                .retrieve().toBodilessEntity();
            throw new AssertionError("Esperado 403");
        } catch (RestClientResponseException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void shouldRejectUpdateWithBlankName() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        try {
            client.put().uri("/api/v1/tenants/me")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "  "))
                .retrieve().toBodilessEntity();
            throw new AssertionError("Esperado 400");
        } catch (RestClientResponseException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(400);
        }
    }

    // ---------------------------------------------------------------- SPI-57

    @Test
    void shouldBlockLoginAndProtectedRequestsWhenTenantInactive() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        assertThat(adminToken).isNotBlank();
        assertThat(protectedStatus(adminToken)).isEqualTo(200);

        Tenant tenant = tenantRepository.findBySlug(TENANT_SLUG).orElseThrow();
        tenantRepository.save(tenant.deactivate());

        // INACTIVE bloqueia login e requests protegidos (mesmo com token válido).
        assertThat(loginStatus(ADMIN_EMAIL, ADMIN_PASSWORD)).isEqualTo(401);
        assertThat(protectedStatus(adminToken)).isEqualTo(401);

        // Reativação restaura o acesso (ACTIVE permite login).
        Tenant inactive = tenantRepository.findBySlug(TENANT_SLUG).orElseThrow();
        tenantRepository.save(inactive.activate());

        assertThat(loginStatus(ADMIN_EMAIL, ADMIN_PASSWORD)).isEqualTo(200);
    }

    // ---------------------------------------------------------------- SPI-53

    private Map<?, ?> getBranding(String token) {
        return client.get().uri("/api/v1/tenants/me/identidade-visual")
            .header("Authorization", "Bearer " + token)
            .retrieve().body(Map.class);
    }

    private Map<?, ?> putBranding(String token, Map<String, Object> body) {
        return client.put().uri("/api/v1/tenants/me/identidade-visual")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve().body(Map.class);
    }

    @Test
    void shouldReturnBrandingOfCurrentTenant() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        // Estado limpo: o contexto Spring é compartilhado entre os testes da classe.
        putBranding(adminToken, Map.of("logoUrl", "", "primaryColor", "", "secondaryColor", ""));
        Map<?, ?> branding = getBranding(adminToken);

        assertThat(branding).isNotNull();
        assertThat(branding.get("tenantId")).isNotNull();
        assertThat(branding.get("logoUrl")).isNull();
        assertThat(branding.get("primaryColor")).isNull();
    }

    @Test
    void shouldUpdateBranding() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        Map<?, ?> updated = putBranding(adminToken, Map.of(
            "logoUrl", "https://cdn.example.com/logo.png",
            "primaryColor", "#1a2b3c",
            "secondaryColor", "#ffffff"));

        assertThat(updated).isNotNull();
        assertThat(updated.get("logoUrl")).isEqualTo("https://cdn.example.com/logo.png");
        assertThat(updated.get("primaryColor")).isEqualTo("#1A2B3C");
        assertThat(updated.get("secondaryColor")).isEqualTo("#FFFFFF");
        assertThat(getBranding(adminToken).get("primaryColor")).isEqualTo("#1A2B3C");
    }

    @Test
    void shouldRejectInvalidBrandingColor() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        try {
            putBranding(adminToken, Map.of("primaryColor", "azul"));
            throw new AssertionError("Esperado 400");
        } catch (RestClientResponseException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Test
    void shouldRejectBrandingUpdateWithoutTenantManagePermission() {
        String collaboratorToken = login("colab@tenantmgmt.com", ADMIN_PASSWORD);

        try {
            putBranding(collaboratorToken, Map.of("primaryColor", "#000000"));
            throw new AssertionError("Esperado 403");
        } catch (RestClientResponseException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(403);
        }
    }
}
