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

/** EN03/EN07/EN11: adoção, recorte por visibilidade e fontes isoladas. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = GovernanceApplication.class)
class EngagementAdoptionIT {

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

    private static final LocalDate ATE = LocalDate.of(2026, 7, 31);
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
    private String inactiveEmail;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        String slug = "engadopt" + UUID.randomUUID().toString().substring(0, 8);
        adminEmail = "admin@" + slug + ".com";
        executiveEmail = "exec@" + slug + ".com";
        managerAEmail = "manager.a@" + slug + ".com";
        collabAEmail = "collab.a@" + slug + ".com";
        managerBEmail = "manager.b@" + slug + ".com";
        collabBEmail = "collab.b@" + slug + ".com";
        inactiveEmail = "inactive@" + slug + ".com";

        tenant = testTenants.ensureTenant(slug, "Engagement Adoption", "Admin",
            adminEmail, PASSWORD);
        User admin = testTenants.admin(tenant, adminEmail);
        departmentA = createDepartment("A");
        departmentB = createDepartment("B");

        User executive = testTenants.ensureMember(tenant, admin.id(), executiveEmail,
            "Executive", PASSWORD, HierarchyLevel.EXECUTIVE, null, "EXECUTIVE");
        User managerA = testTenants.ensureMember(tenant, admin.id(), managerAEmail,
            "Manager A", PASSWORD, HierarchyLevel.MANAGER, departmentA, "MANAGER");
        User collabA = testTenants.ensureMember(tenant, admin.id(), collabAEmail,
            "Collab A", PASSWORD, HierarchyLevel.COLLABORATOR, departmentA, "COLLABORATOR");
        User managerB = testTenants.ensureMember(tenant, admin.id(), managerBEmail,
            "Manager B", PASSWORD, HierarchyLevel.MANAGER, departmentB, "MANAGER");
        User collabB = testTenants.ensureMember(tenant, admin.id(), collabBEmail,
            "Collab B", PASSWORD, HierarchyLevel.COLLABORATOR, departmentB, "COLLABORATOR");
        User inactive = testTenants.ensureMember(tenant, admin.id(), inactiveEmail,
            "Inactive", PASSWORD, HierarchyLevel.COLLABORATOR, departmentA, "COLLABORATOR");
        inactive.deactivate();
        userRepository.save(inactive);

        operationalUsage(managerA, "2026-07-15T10:00:00Z");
        operationalUsage(collabA, "2026-07-15T10:00:00Z");
        operationalUsage(collabB, "2026-07-15T10:00:00Z");

        link(executive, "E-EXEC");
        link(managerA, "E-MANAGER-A");
        link(collabA, "E-COLLAB-A");
        link(managerB, "E-MANAGER-B");
        link(collabB, "E-COLLAB-B");
        historicalUsage("E-COLLAB-A", "2026-07-15T10:00:00");
        historicalUsage("E-COLLAB-B", "2026-07-15T10:00:00");
    }

    @Test
    void en03_operacionalBateComUsageRecordEExcluiAdminEInativo() {
        Map<String, Object> response = get(executiveEmail,
            "/api/v1/engajamento/adocao?ate=" + ATE);

        assertThat(response.get("sourceKind")).isEqualTo("OPERACIONAL");
        assertThat(response.get("dataStatus")).isEqualTo("COMPLETE");
        assertThat(response.get("elegiveis")).isEqualTo(5);
        assertThat(response.get("ativos")).isEqualTo(3);
        assertThat(response.get("inativos")).isEqualTo(2);
        assertThat(new BigDecimal(response.get("taxaAdocao").toString()))
            .isEqualByComparingTo("60.00");
        assertThat(response.get("janelaDias")).isEqualTo(30);

        Integer distinctUsers = jdbcTemplate.queryForObject(
            "SELECT count(DISTINCT user_id) FROM observability_usage_records "
                + "WHERE tenant_id = ? AND created_at >= ? AND created_at < ?",
            Integer.class, tenant.id().value(),
            Timestamp.from(LocalDate.of(2026, 7, 2).atStartOfDay(ZoneOffset.UTC).toInstant()),
            Timestamp.from(LocalDate.of(2026, 8, 1).atStartOfDay(ZoneOffset.UTC).toInstant()));
        assertThat(response.get("ativos")).isEqualTo(distinctUsers);

        List<?> departments = (List<?>) response.get("porDepartamento");
        Map<?, ?> a = department(departments, departmentA);
        Map<?, ?> b = department(departments, departmentB);
        assertThat(a.get("elegiveis")).isEqualTo(2);
        assertThat(a.get("ativos")).isEqualTo(2);
        assertThat(new BigDecimal(a.get("taxaAdocao").toString()))
            .isEqualByComparingTo("100.00");
        assertThat(b.get("elegiveis")).isEqualTo(2);
        assertThat(b.get("ativos")).isEqualTo(1);
        assertThat(new BigDecimal(b.get("taxaAdocao").toString()))
            .isEqualByComparingTo("50.00");

        assertThat(status(adminEmail, "/api/v1/engajamento/adocao?ate=" + ATE))
            .isEqualTo(403);
    }

    @Test
    void en07_managerVeSoSeuDepartamentoECollaboratorSoProprio() {
        Map<String, Object> manager = get(managerAEmail,
            "/api/v1/engajamento/adocao?ate=" + ATE);
        assertThat(manager.get("elegiveis")).isEqualTo(2);
        assertThat(manager.get("ativos")).isEqualTo(2);
        assertThat(((List<?>) manager.get("porDepartamento"))).hasSize(1);
        assertThat(status(managerAEmail,
            "/api/v1/engajamento/adocao?ate=" + ATE + "&departmentId=" + departmentB))
            .isEqualTo(403);

        Map<String, Object> collaborator = get(collabAEmail,
            "/api/v1/engajamento/adocao?ate=" + ATE);
        assertThat(collaborator.get("elegiveis")).isEqualTo(1);
        assertThat(collaborator.get("ativos")).isEqualTo(1);
        assertThat(status(collabAEmail,
            "/api/v1/engajamento/adocao?ate=" + ATE + "&departmentId=" + departmentA))
            .isEqualTo(403);
    }

    @Test
    void en11_fontesNuncaSeMisturam() {
        Map<String, Object> operational = get(executiveEmail,
            "/api/v1/engajamento/adocao?ate=" + ATE + "&fonte=OPERACIONAL");
        Map<String, Object> historical = get(executiveEmail,
            "/api/v1/engajamento/adocao?ate=" + ATE + "&fonte=HISTORICO");

        assertThat(operational.get("sourceKind")).isEqualTo("OPERACIONAL");
        assertThat(historical.get("sourceKind")).isEqualTo("HISTORICO");
        assertThat(operational.get("ativos")).isEqualTo(3);
        assertThat(historical.get("ativos")).isEqualTo(2);
        assertThat(historical.get("elegiveis")).isEqualTo(5);
        assertThat(new BigDecimal(historical.get("taxaAdocao").toString()))
            .isEqualByComparingTo("40.00");
        assertThat(historical.get("ativos")).isNotEqualTo(operational.get("ativos"));
        assertThat(((Map<?, ?>) historical.get("cobertura")).get("eligibleLinked"))
            .isEqualTo(5);
    }

    @Test
    void rejeitaFonteInvalidaEAteFuturo() {
        assertThat(status(executiveEmail,
            "/api/v1/engajamento/adocao?ate=" + ATE + "&fonte=INVALIDA")).isEqualTo(400);
        assertThat(status(executiveEmail,
            "/api/v1/engajamento/adocao?ate=" + LocalDate.now(ZoneOffset.UTC).plusDays(1)))
            .isEqualTo(400);
    }

    private UUID createDepartment(String name) {
        Map<String, Object> response = post(adminEmail, "/api/v1/departamentos",
            Map.of("name", name + " " + UUID.randomUUID()));
        return UUID.fromString((String) response.get("id"));
    }

    private Map<?, ?> department(List<?> departments, UUID id) {
        return departments.stream()
            .map(item -> (Map<?, ?>) item)
            .filter(item -> id.toString().equals(item.get("departmentId")))
            .findFirst().orElseThrow();
    }

    private void link(User user, String employeeId) {
        jdbcTemplate.update("""
            INSERT INTO iam_legacy_user_links (tenant_id, user_id, legacy_employee_id)
            VALUES (?, ?, ?)
            ON CONFLICT (tenant_id, user_id) DO NOTHING
            """, tenant.id().value(), user.id().value(), employeeId);
    }

    private void operationalUsage(User user, String instant) {
        jdbcTemplate.update("""
            INSERT INTO observability_usage_records
                (id, request_id, tenant_id, user_id, input_tokens, output_tokens,
                 estimated_cost, latency_ms, status, created_at)
            VALUES (?, ?, ?, ?, 1, 1, 0, 1, 'COMPLETED', ?)
            """, UUID.randomUUID(), UUID.randomUUID(), tenant.id().value(), user.id().value(),
            Timestamp.from(Instant.parse(instant)));
    }

    private void historicalUsage(String employeeId, String timestamp) {
        jdbcTemplate.update("""
            INSERT INTO historical.ai_usage_logs
                (usage_id, "timestamp", employee_id, company_id, request_count)
            VALUES (?, ?, ?, ?, 1)
            """, UUID.randomUUID().toString(), timestamp, employeeId,
            tenant.id().value().toString());
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
