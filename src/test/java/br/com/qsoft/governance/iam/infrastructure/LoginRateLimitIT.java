package br.com.qsoft.governance.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.iam.application.ProvisionUser;
import br.com.qsoft.governance.iam.application.ProvisionUserCommand;
import br.com.qsoft.governance.iam.application.RegisterTenant;
import br.com.qsoft.governance.iam.application.RegisterTenantCommand;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantSlugAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
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
 * Teste de integração do rate limiting do login (SPI-67): após N tentativas
 * inválidas o login responde 429 temporariamente, sem revelar se a conta
 * existe, sem bloquear outras contas e sem lockout permanente. Exige Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = br.com.qsoft.governance.GovernanceApplication.class)
class LoginRateLimitIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:18.6-alpine")
            .withDatabaseName("qsoft_test")
            .withUsername("qsoft")
            .withPassword("qsoft_test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.login-rate-limit.max-attempts", () -> "3");
        registry.add("app.security.login-rate-limit.window-minutes", () -> "15");
    }

    private static final String TENANT_SLUG = "ratedemo";
    private static final String USER_EMAIL = "user@ratedemo.com";
    private static final String BLOCKED_USER_EMAIL = "blockeduser@ratedemo.com";
    private static final String USER_PASSWORD = "secret123";
    private static final int MAX_ATTEMPTS = 3;

    @LocalServerPort
    int port;

    @Autowired
    RegisterTenant registerTenant;

    @Autowired
    ProvisionUser provisionUser;

    @Autowired
    TenantRepository tenantRepository;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        Tenant tenant = ensureTenant();
        ensureUser(tenant, USER_EMAIL);
        ensureUser(tenant, BLOCKED_USER_EMAIL);
    }

    private Tenant ensureTenant() {
        try {
            return registerTenant.register(
                new RegisterTenantCommand(TENANT_SLUG, "Rate Demo", TENANT_SLUG + ".com"));
        } catch (TenantSlugAlreadyExistsException ignored) {
            return tenantRepository.findBySlug(TENANT_SLUG).orElseThrow();
        }
    }

    private void ensureUser(Tenant tenant, String email) {
        try {
            provisionUser.provision(new ProvisionUserCommand(
                tenant.id(), email, "User", HierarchyLevel.COLLABORATOR,
                USER_PASSWORD, null, Set.of()));
        } catch (UserEmailAlreadyExistsException ignored) {
            // já provisionado por um teste anterior
        }
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

    private void exhaust(String email) {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            assertThat(loginStatus(email, "senha-errada")).isEqualTo(401);
        }
    }

    private String retryAfterHeader(String email, String password) {
        try {
            client.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .retrieve().toBodilessEntity();
            return null;
        } catch (RestClientResponseException e) {
            return e.getResponseHeaders() == null
                ? null
                : e.getResponseHeaders().getFirst("Retry-After");
        }
    }

    @Test
    void shouldBlockTemporarilyAfterMaxInvalidAttempts() {
        String email = "blocked@ratedemo.com";

        exhaust(email);

        assertThat(loginStatus(email, "senha-errada")).isEqualTo(429);

        // O 429 informa quando o cliente pode tentar novamente (RFC 6585).
        String retryAfter = retryAfterHeader(email, "senha-errada");
        assertThat(retryAfter).isNotNull();
        assertThat(Long.parseLong(retryAfter)).isPositive();
    }

    @Test
    void shouldKeepBlockedEvenWithCorrectPasswordUntilWindowExpires() {
        exhaust(BLOCKED_USER_EMAIL);

        assertThat(loginStatus(BLOCKED_USER_EMAIL, USER_PASSWORD)).isEqualTo(429);
    }

    @Test
    void shouldNotRevealWhetherAccountExists() {
        String ghost = "ghost@ratedemo.com";

        exhaust(ghost);

        assertThat(loginStatus(ghost, "senha-errada")).isEqualTo(429);
    }

    @Test
    void shouldNotBlockOtherAccounts() {
        String blocked = "blocked2@ratedemo.com";
        String other = "other2@ratedemo.com";

        exhaust(blocked);

        assertThat(loginStatus(blocked, "senha-errada")).isEqualTo(429);
        assertThat(loginStatus(other, "senha-errada")).isEqualTo(401);
    }

    @Test
    void shouldResetCounterOnSuccessfulLogin() {
        // Duas falhas (abaixo do limite), sucesso (zera), duas falhas -> ainda 401.
        assertThat(loginStatus(USER_EMAIL, "senha-errada")).isEqualTo(401);
        assertThat(loginStatus(USER_EMAIL, "senha-errada")).isEqualTo(401);
        assertThat(loginStatus(USER_EMAIL, USER_PASSWORD)).isEqualTo(200);
        assertThat(loginStatus(USER_EMAIL, "senha-errada")).isEqualTo(401);
        assertThat(loginStatus(USER_EMAIL, "senha-errada")).isEqualTo(401);
    }
}
