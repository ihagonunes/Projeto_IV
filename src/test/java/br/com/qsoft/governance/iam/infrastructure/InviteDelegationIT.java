package br.com.qsoft.governance.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.rbac.GrantOrigin;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.support.TestTenants;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.ScopeType;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * ITs de delegação de convites (SPI-46 / ADR-37): AZ02 (concessão INVITE),
 * AZ07 (dominância do MANAGER), AZ13 (token hash/uso único/log) e AZ17
 * (convidante perde autoridade → 409). Exige Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = br.com.qsoft.governance.GovernanceApplication.class,
    properties = "app.bootstrap.demo-data=false")
class InviteDelegationIT {

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

    private static final String SLUG = "invitedelegdemo";
    private static final String ADMIN_EMAIL = "admin@invitedelegdemo.com";
    private static final String MANAGER_EMAIL = "manager@invitedelegdemo.com";
    private static final String PASSWORD = "secret123";
    private static final UUID DEPT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @LocalServerPort
    int port;

    @Autowired
    TestTenants testTenants;

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private RestClient client;
    private Tenant tenant;
    private UserId adminId;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        tenant = testTenants.ensureTenant(SLUG, "Invite Deleg", "Admin", ADMIN_EMAIL, PASSWORD);
        adminId = testTenants.admin(tenant, ADMIN_EMAIL).id();
        User manager = testTenants.ensureMember(tenant, adminId, MANAGER_EMAIL, "Manager",
            PASSWORD, HierarchyLevel.MANAGER, DEPT_A, "MANAGER");
        if (manager.status() != br.com.qsoft.governance.iam.domain.user.UserStatus.ACTIVE) {
            userRepository.save(manager.activate());
        }
    }

    private String login(String email) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", PASSWORD))
            .retrieve().body(Map.class);
        return response == null ? null : (String) response.get("token");
    }

    private Map<?, ?> invite(String token, String email, String roleKey, UUID departmentId) {
        var body = new java.util.HashMap<String, Object>();
        body.put("email", email);
        body.put("roleKey", roleKey);
        if (departmentId != null) {
            body.put("departmentId", departmentId.toString());
        }
        return client.post().uri("/api/v1/convites")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve().body(Map.class);
    }

    private int inviteStatus(String token, String email, String roleKey, UUID departmentId) {
        var body = new java.util.HashMap<String, Object>();
        body.put("email", email);
        body.put("roleKey", roleKey);
        if (departmentId != null) {
            body.put("departmentId", departmentId.toString());
        }
        try {
            return client.post().uri("/api/v1/convites")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException e) {
            return e.getStatusCode().value();
        }
    }

    private int accept(String rawToken, String password) {
        try {
            client.post().uri("/api/v1/convites/aceitar")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("token", rawToken, "name", "Novo", "password", password))
                .retrieve().toBodilessEntity();
            return 200;
        } catch (RestClientResponseException e) {
            return e.getStatusCode().value();
        }
    }

    private static String sha256(String raw) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void az02InviteCarriesTheGrant() {
        String adminToken = login(ADMIN_EMAIL);
        String email = "az02@invitedelegdemo.com";
        Map<?, ?> invite = invite(adminToken, email, "COLLABORATOR", null);
        String raw = (String) invite.get("token");

        assertThat(accept(raw, "senha123")).isEqualTo(200);

        User member = userRepository.findByTenantIdAndEmail(tenant.id(), email).orElseThrow();
        var grant = roleAssignmentRepository
            .findActiveByTenantIdAndUserId(tenant.id(), member.id())
            .stream().findFirst().orElseThrow();
        assertThat(grant.origin()).isEqualTo(GrantOrigin.INVITE);
        assertThat(grant.grantedBy()).isEqualTo(adminId);
        assertThat(grant.scopeType()).isEqualTo(ScopeType.SELF);
    }

    @Test
    void az07ManagerOnlyDelegatesLowerRolesWithinItsDepartment() {
        String managerToken = login(MANAGER_EMAIL);

        assertThat(inviteStatus(managerToken, "az07a@invitedelegdemo.com", "MANAGER", DEPT_A))
            .isEqualTo(403);
        assertThat(inviteStatus(managerToken, "az07b@invitedelegdemo.com", "EXECUTIVE", null))
            .isEqualTo(403);
        assertThat(inviteStatus(managerToken, "az07c@invitedelegdemo.com", "TENANT_ADMIN", null))
            .isEqualTo(403);
        assertThat(inviteStatus(managerToken, "az07d@invitedelegdemo.com", "LEADER", UUID.randomUUID()))
            .isEqualTo(403);

        assertThat(inviteStatus(managerToken, "az07e@invitedelegdemo.com", "LEADER", DEPT_A))
            .isEqualTo(201);
        assertThat(inviteStatus(managerToken, "az07f@invitedelegdemo.com", "COLLABORATOR", DEPT_A))
            .isEqualTo(201);
    }

    @Test
    void az13TokenIsHashedSingleUseAndNeverLogged() {
        String adminToken = login(ADMIN_EMAIL);
        String email = "az13@invitedelegdemo.com";
        Map<?, ?> invite = invite(adminToken, email, "COLLABORATOR", null);
        String raw = (String) invite.get("token");
        UUID inviteId = UUID.fromString((String) invite.get("id"));

        // O banco guarda apenas o SHA-256 em hex.
        String stored = jdbcTemplate.queryForObject(
            "SELECT token_hash FROM iam_invites WHERE id = ?", String.class, inviteId);
        assertThat(stored).isEqualTo(sha256(raw)).isNotEqualTo(raw);

        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            assertThat(accept(raw, "senha123")).isEqualTo(200);
            // Reutilização e token adulterado são rejeitados.
            assertThat(accept(raw, "senha123")).isEqualTo(400);
            assertThat(accept("token-adulterado-0000", "senha123")).isEqualTo(400);
        } finally {
            root.detachAppender(appender);
        }
        assertThat(appender.list)
            .as("o token puro nunca pode aparecer em log")
            .noneSatisfy(event -> assertThat(event.getFormattedMessage()).contains(raw));

        // Convite expirado.
        Map<?, ?> expiring = invite(adminToken, "az13b@invitedelegdemo.com", "COLLABORATOR", null);
        UUID expiringId = UUID.fromString((String) expiring.get("id"));
        String expiringToken = (String) expiring.get("token");
        jdbcTemplate.update(
            "UPDATE iam_invites SET expires_at = now() - interval '1 hour' WHERE id = ?", expiringId);
        assertThat(accept(expiringToken, "senha123")).isEqualTo(400);

        // A listagem nunca devolve o token.
        List<Map<String, Object>> invites = client.get().uri("/api/v1/convites")
            .header("Authorization", "Bearer " + adminToken)
            .retrieve().body(new org.springframework.core.ParameterizedTypeReference<>() {
            });
        assertThat(invites).allSatisfy(item -> assertThat(item).doesNotContainKey("token"));
    }

    @Test
    void az17AcceptIsRejectedWhenInviterLostAuthority() {
        String managerToken = login(MANAGER_EMAIL);
        String email = "az17@invitedelegdemo.com";
        Map<?, ?> invite = invite(managerToken, email, "COLLABORATOR", DEPT_A);
        String raw = (String) invite.get("token");

        // O convidante perde a autoridade (usuário desativado) antes do aceite.
        User manager = userRepository.findByTenantIdAndEmail(tenant.id(), MANAGER_EMAIL).orElseThrow();
        userRepository.save(manager.deactivate());

        assertThat(accept(raw, "senha123")).isEqualTo(409);
        assertThat(userRepository.findByTenantIdAndEmail(tenant.id(), email)).isEmpty();
    }
}
