package br.com.qsoft.governance.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import br.com.qsoft.governance.iam.application.BootstrapTenantAdmin;
import br.com.qsoft.governance.iam.application.RegisterTenant;
import br.com.qsoft.governance.iam.application.RegisterTenantCommand;
import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.rbac.GrantOrigin;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.User;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * IT do bootstrap do primeiro administrador (SPI-169 / ADR-37 item 1):
 * AZ01 (registrante vira TENANT_ADMIN e administra o tenant) e a atomicidade
 * da transação de registro (falha após criar o tenant reverte tudo). Exige Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = br.com.qsoft.governance.GovernanceApplication.class,
    properties = "app.bootstrap.demo-data=false")
class TenantBootstrapIT {

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

    private static final String ADMIN_PASSWORD = "admin-secret-123";

    @LocalServerPort
    int port;

    @Autowired
    RegisterTenant registerTenant;

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    BootstrapTenantAdmin bootstrapTenantAdmin;

    private RestClient client() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    private String login(RestClient client, String email, String password) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", password))
            .retrieve().body(Map.class);
        return response == null ? null : (String) response.get("token");
    }

    private int putTenantName(RestClient client, String token, String name) {
        try {
            client.put().uri("/api/v1/tenants/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name))
                .retrieve().toBodilessEntity();
            return 200;
        } catch (RestClientResponseException e) {
            return e.getStatusCode().value();
        }
    }

    @Test
    void az01RegistrantBecomesTenantAdminAndAdministersTheTenant() {
        String slug = "az01-" + UUID.randomUUID();
        String adminEmail = "dona@" + slug + ".com";
        RestClient client = client();

        client.post().uri("/api/v1/tenants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of(
                "slug", slug,
                "name", "AZ01",
                "emailDomain", slug + ".com",
                "adminName", "Dona",
                "adminEmail", adminEmail,
                "adminPassword", ADMIN_PASSWORD))
            .retrieve().toBodilessEntity();

        // O registrante loga e administra o tenant.
        String token = login(client, adminEmail, ADMIN_PASSWORD);
        assertThat(token).isNotBlank();
        assertThat(putTenantName(client, token, "AZ01 Renomeado")).isEqualTo(200);

        // A concessão é TENANT_ADMIN/TENANT com origem BOOTSTRAP.
        Tenant tenant = tenantRepository.findBySlug(slug).orElseThrow();
        User admin = userRepository.findByTenantIdAndEmail(tenant.id(), adminEmail).orElseThrow();
        var grant = roleAssignmentRepository
            .findActiveByTenantIdAndUserId(tenant.id(), admin.id())
            .stream()
            .findFirst()
            .orElseThrow();
        assertThat(grant.origin()).isEqualTo(GrantOrigin.BOOTSTRAP);
        assertThat(grant.scopeType().name()).isEqualTo("TENANT");
    }

    @Test
    void registrationRollsBackEverythingWhenBootstrapFails() {
        String slug = "rollback-" + UUID.randomUUID();
        String adminEmail = "admin@" + slug + ".com";

        doThrow(new IllegalStateException("falha simulada no bootstrap"))
            .when(bootstrapTenantAdmin).bootstrap(any(), any(), any(), any());

        assertThatThrownBy(() -> registerTenant.register(new RegisterTenantCommand(
            slug, "Rollback", slug + ".com", "Admin", adminEmail, ADMIN_PASSWORD)))
            .isInstanceOf(IllegalStateException.class);

        // Nada foi persistido: nem tenant, nem usuário.
        assertThat(tenantRepository.findBySlug(slug)).isEmpty();
        Integer users = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM app_user WHERE email = ?", Integer.class, adminEmail);
        assertThat(users).isZero();
    }
}
