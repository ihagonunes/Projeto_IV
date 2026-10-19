package br.com.qsoft.governance.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.iam.application.ProvisionUser;
import br.com.qsoft.governance.iam.application.ProvisionUserCommand;
import br.com.qsoft.governance.iam.application.RegisterTenant;
import br.com.qsoft.governance.iam.application.RegisterTenantCommand;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantSlugAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Teste de integração da expiração de token (SPI-66): um JWT expirado bloqueia
 * o acesso imediatamente e de forma uniforme nos endpoints protegidos; não há
 * refresh token — novo acesso exige novo login (ADR-30). Exige Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = br.com.qsoft.governance.GovernanceApplication.class)
class TokenExpirationIT {

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

    private static final String TENANT_SLUG = "expiredemo";
    private static final String USER_EMAIL = "user@expiredemo.com";
    private static final String USER_PASSWORD = "secret123";

    /** Endpoints protegidos usados para provar o comportamento uniforme. */
    private static final String[] PROTECTED_ENDPOINTS = {
        "/api/v1/tenants/me",
        "/api/v1/usuarios/me/permissoes",
        "/api/v1/usuarios"
    };

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
    JwtEncoder jwtEncoder;

    private RestClient client;
    private UUID userId;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        Tenant tenant = ensureTenant();
        tenantId = tenant.id().value();
        userId = ensureUser(tenant).id().value();
    }

    private Tenant ensureTenant() {
        try {
            return registerTenant.register(
                new RegisterTenantCommand(TENANT_SLUG, "Expire Demo", TENANT_SLUG + ".com",
                    "Admin", "admin@" + TENANT_SLUG + ".com", "secret123"));
        } catch (TenantSlugAlreadyExistsException ignored) {
            return tenantRepository.findBySlug(TENANT_SLUG).orElseThrow();
        }
    }

    private User ensureUser(Tenant tenant) {
        try {
            return provisionUser.provision(new ProvisionUserCommand(
                tenant.id(), USER_EMAIL, "User", HierarchyLevel.COLLABORATOR,
                USER_PASSWORD, null, Set.of()));
        } catch (UserEmailAlreadyExistsException ignored) {
            return userRepository.findByTenantIdAndEmail(tenant.id(), USER_EMAIL).orElseThrow();
        }
    }

    /** Assina um JWT já expirado com a mesma chave do servidor (HS256). */
    private String expiredToken() {
        Instant past = Instant.now().minusSeconds(3600);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(userId.toString())
            .claim("tenant_id", tenantId.toString())
            .issuedAt(past.minusSeconds(60))
            .expiresAt(past)
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String validToken() {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", USER_EMAIL, "password", USER_PASSWORD))
            .retrieve().body(Map.class);
        return response == null ? null : (String) response.get("token");
    }

    private int statusWithToken(String token, String uri) {
        try {
            client.get().uri(uri)
                .header("Authorization", "Bearer " + token)
                .retrieve().toBodilessEntity();
            return 200;
        } catch (RestClientResponseException e) {
            return e.getStatusCode().value();
        }
    }

    private int statusWithoutToken(String uri) {
        try {
            client.get().uri(uri).retrieve().toBodilessEntity();
            return 200;
        } catch (RestClientResponseException e) {
            return e.getStatusCode().value();
        }
    }

    @Test
    void shouldBlockExpiredTokenImmediatelyOnEveryProtectedEndpoint() {
        String expired = expiredToken();

        for (String endpoint : PROTECTED_ENDPOINTS) {
            assertThat(statusWithToken(expired, endpoint))
                .as("endpoint %s deve responder 401 para token expirado", endpoint)
                .isEqualTo(401);
        }
    }

    @Test
    void shouldBehaveUniformlyForExpiredAndMissingToken() {
        String expired = expiredToken();

        for (String endpoint : PROTECTED_ENDPOINTS) {
            assertThat(statusWithToken(expired, endpoint))
                .as("token expirado em %s", endpoint)
                .isEqualTo(401);
            assertThat(statusWithoutToken(endpoint))
                .as("sem token em %s", endpoint)
                .isEqualTo(401);
        }
    }

    @Test
    void shouldAcceptFreshlyIssuedToken() {
        assertThat(statusWithToken(validToken(), "/api/v1/tenants/me")).isEqualTo(200);
    }

    /** Não existe refresh token no MVP (ADR-30): novo acesso exige novo login. */
    @Test
    void shouldNotExposeRefreshEndpoint() {
        try {
            client.post().uri("/api/v1/auth/refresh")
                .header("Authorization", "Bearer " + validToken())
                .retrieve().toBodilessEntity();
            throw new AssertionError("Esperado 404 — não deve existir endpoint de refresh");
        } catch (RestClientResponseException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(404);
        }
    }
}
