package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.GovernanceApplication;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.support.TestTenants;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

/** EN05/EN06/EN07: credits, snapshot imutável e recorte de visibilidade. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = GovernanceApplication.class)
class EngagementCreditsIT {

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

    private static final YearMonth MONTH = YearMonth.of(2026, 7);
    private static final String PASSWORD = "secret123";

    @LocalServerPort
    int port;

    @Autowired
    TestTenants testTenants;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private RestClient client;
    private Tenant tenant;
    private UUID departmentA;
    private UUID departmentB;
    private String adminEmail;
    private String executiveEmail;
    private String managerAEmail;
    private String collabAEmail;
    private String managerBEmail;
    private String collabBEmail;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        String slug = "engcredits" + UUID.randomUUID().toString().substring(0, 8);
        adminEmail = "admin@" + slug + ".com";
        executiveEmail = "exec@" + slug + ".com";
        managerAEmail = "manager.a@" + slug + ".com";
        collabAEmail = "collab.a@" + slug + ".com";
        managerBEmail = "manager.b@" + slug + ".com";
        collabBEmail = "collab.b@" + slug + ".com";

        tenant = testTenants.ensureTenant(slug, "Engagement Credits", "Admin",
            adminEmail, PASSWORD);
        User admin = testTenants.admin(tenant, adminEmail);
        departmentA = createDepartment("A");
        departmentB = createDepartment("B");
        testTenants.ensureMember(tenant, admin.id(), executiveEmail, "Executive", PASSWORD,
            HierarchyLevel.EXECUTIVE, null, "EXECUTIVE");
        User managerA = testTenants.ensureMember(tenant, admin.id(), managerAEmail, "Manager A",
            PASSWORD, HierarchyLevel.MANAGER, departmentA, "MANAGER");
        User collabA = testTenants.ensureMember(tenant, admin.id(), collabAEmail, "Collab A",
            PASSWORD, HierarchyLevel.COLLABORATOR, departmentA, "COLLABORATOR");
        testTenants.ensureMember(tenant, admin.id(), managerBEmail, "Manager B", PASSWORD,
            HierarchyLevel.MANAGER, departmentB, "MANAGER");
        User collabB = testTenants.ensureMember(tenant, admin.id(), collabBEmail, "Collab B",
            PASSWORD, HierarchyLevel.COLLABORATOR, departmentB, "COLLABORATOR");

        usage(managerA, "2026-07-15T10:00:00Z");
        usage(collabA, "2026-07-15T10:00:00Z");
        usage(collabB, "2026-07-15T10:00:00Z");
        training(managerA, LocalDate.of(2026, 7, 10));
        license(managerA);
    }

    @Test
    void en05_calculaCreditosEPersisteSnapshotDoMesFechado() {
        Map<String, Object> response = get(executiveEmail,
            "/api/v1/engajamento/creditos?mes=" + MONTH);

        assertThat(response.get("sourceKind")).isEqualTo("OPERACIONAL");
        assertThat(response.get("mes")).isEqualTo("2026-07");
        assertThat(response.get("status")).isEqualTo("FECHADO");
        assertThat(response.get("calculationVersion")).isEqualTo("credits-v1");
        List<?> items = (List<?>) response.get("itens");
        assertThat(items).hasSize(5);

        User manager = userRepository.findByTenantIdAndEmail(tenant.id(), managerAEmail)
            .orElseThrow();
        Map<?, ?> managerItem = item(items, manager.id().value());
        assertThat(managerItem.get("score")).isNotNull();
        assertThat(new BigDecimal(managerItem.get("score").toString()))
            .isEqualByComparingTo("55.00");
        assertThat(managerItem.get("licenseApplicability")).isEqualTo("APPLICABLE");

        Integer snapshots = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM engagement_credit_snapshots "
                + "WHERE tenant_id = ? AND period = ?",
            Integer.class, tenant.id().value(), java.sql.Date.valueOf(MONTH.atDay(1)));
        assertThat(snapshots).isEqualTo(5);
    }

    @Test
    void en06_snapshotDoMesFechadoEhImutavel() {
        Map<String, Object> first = get(executiveEmail,
            "/api/v1/engajamento/creditos?mes=" + MONTH);
        User collab = userRepository.findByTenantIdAndEmail(tenant.id(), collabAEmail)
            .orElseThrow();
        BigDecimal firstScore = score(first, collab.id().value());

        usage(collab, "2026-07-16T10:00:00Z");
        usage(collab, "2026-07-17T10:00:00Z");
        training(collab, LocalDate.of(2026, 7, 20));

        Map<String, Object> second = get(executiveEmail,
            "/api/v1/engajamento/creditos?mes=" + MONTH);
        assertThat(score(second, collab.id().value())).isEqualByComparingTo(firstScore);
    }

    @Test
    void en07_collaboratorVeSoPropriaLinhaEManagerRespeitaDepartamento() {
        Map<String, Object> manager = get(managerAEmail,
            "/api/v1/engajamento/creditos?mes=" + MONTH);
        assertThat((List<?>) manager.get("itens")).hasSize(2);
        assertThat(status(managerAEmail,
            "/api/v1/engajamento/creditos?mes=" + MONTH + "&departmentId=" + departmentB))
            .isEqualTo(403);

        Map<String, Object> collab = get(collabAEmail,
            "/api/v1/engajamento/creditos?mes=" + MONTH);
        List<?> collabItems = (List<?>) collab.get("itens");
        assertThat(collabItems).hasSize(1);
        User collabUser = userRepository.findByTenantIdAndEmail(tenant.id(), collabAEmail)
            .orElseThrow();
        assertThat(((Map<?, ?>) collabItems.get(0)).get("userId"))
            .isEqualTo(collabUser.id().value().toString());
    }

    @Test
    void mesCorrenteEhProvisorioSemPersistirEMesFuturoEh400() {
        YearMonth current = YearMonth.now(ZoneOffset.UTC);
        Map<String, Object> currentResponse = get(executiveEmail,
            "/api/v1/engajamento/creditos?mes=" + current);
        assertThat(currentResponse.get("status")).isEqualTo("PROVISORIO");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM engagement_credit_snapshots WHERE tenant_id = ? AND period = ?",
            Integer.class, tenant.id().value(),
            java.sql.Date.valueOf(current.atDay(1)))).isZero();

        assertThat(status(executiveEmail,
            "/api/v1/engajamento/creditos?mes=" + current.plusMonths(1))).isEqualTo(400);
    }

    private BigDecimal score(Map<String, Object> response, UUID userId) {
        return new BigDecimal(item((List<?>) response.get("itens"), userId)
            .get("score").toString());
    }

    private Map<?, ?> item(List<?> items, UUID userId) {
        return items.stream().map(value -> (Map<?, ?>) value)
            .filter(value -> userId.toString().equals(value.get("userId")))
            .findFirst().orElseThrow();
    }

    private UUID createDepartment(String name) {
        Map<String, Object> response = post(adminEmail, "/api/v1/departamentos",
            Map.of("name", name + " " + UUID.randomUUID()));
        return UUID.fromString((String) response.get("id"));
    }

    private void usage(User user, String instant) {
        jdbcTemplate.update("""
            INSERT INTO observability_usage_records
                (id, request_id, tenant_id, user_id, input_tokens, output_tokens,
                 estimated_cost, latency_ms, status, created_at)
            VALUES (?, ?, ?, ?, 1, 1, 0, 1, 'COMPLETED', ?)
            """, UUID.randomUUID(), UUID.randomUUID(), tenant.id().value(), user.id().value(),
            Timestamp.from(Instant.parse(instant)));
    }

    private void training(User user, LocalDate completedAt) {
        jdbcTemplate.update("""
            INSERT INTO engagement_training_records
                (id, tenant_id, user_id, course_name, status, completed_at, created_at)
            VALUES (?, ?, ?, 'Governança', 'COMPLETED', ?, now())
            """, UUID.randomUUID(), tenant.id().value(), user.id().value(),
            java.sql.Date.valueOf(completedAt));
    }

    private void license(User user) {
        jdbcTemplate.update("""
            INSERT INTO engagement_ai_licenses
                (id, tenant_id, user_id, ai_tool_id, license_type, status,
                 assigned_at, expires_at, created_at)
            VALUES (?, ?, ?, NULL, 'TEAM', 'ACTIVE', DATE '2026-06-01', NULL, now())
            """, UUID.randomUUID(), tenant.id().value(), user.id().value());
    }

    private Map<String, Object> post(String email, String uri, Object body) {
        return client.post().uri(uri)
            .header("Authorization", "Bearer " + login(email))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve().body(new org.springframework.core.ParameterizedTypeReference<>() {
            });
    }

    private Map<String, Object> get(String email, String uri) {
        return client.get().uri(uri)
            .header("Authorization", "Bearer " + login(email))
            .retrieve().body(new org.springframework.core.ParameterizedTypeReference<>() {
            });
    }

    private int status(String email, String uri) {
        try {
            client.get().uri(uri)
                .header("Authorization", "Bearer " + login(email))
                .retrieve().toBodilessEntity();
            return 200;
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value();
        }
    }

    private String login(String email) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", PASSWORD))
            .retrieve().body(Map.class);
        return response == null ? null : (String) response.get("token");
    }
}
