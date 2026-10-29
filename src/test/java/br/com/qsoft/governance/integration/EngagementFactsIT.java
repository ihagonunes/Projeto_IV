package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.GovernanceApplication;
import br.com.qsoft.governance.engagement.domain.EngagementFacts;
import br.com.qsoft.governance.engagement.domain.LicenseFact;
import br.com.qsoft.governance.engagement.infrastructure.facts.HistoricalEngagementFacts;
import br.com.qsoft.governance.engagement.infrastructure.facts.OperationalEngagementFacts;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.support.TestTenants;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** EN09/EN10: adaptadores operacional e histórico, isolamento e cobertura. */
@Testcontainers
@SpringBootTest(classes = GovernanceApplication.class)
class EngagementFactsIT {

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

    @Autowired
    TestTenants testTenants;

    @Autowired
    UserRepository userRepository;

    @Autowired
    OperationalEngagementFacts operationalFacts;

    @Autowired
    HistoricalEngagementFacts historicalFacts;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Tenant tenant;
    private Tenant other;
    private User executive;
    private User collaborator;
    private User otherMember;

    @BeforeEach
    void setUp() {
        String slug = "engfacts" + UUID.randomUUID().toString().substring(0, 8);
        tenant = testTenants.ensureTenant(slug, "Engagement Facts", "Admin",
            "admin@" + slug + ".com", PASSWORD);
        other = testTenants.ensureTenant(slug + "x", "Engagement Other", "Admin",
            "admin@" + slug + "x.com", PASSWORD);
        User admin = testTenants.admin(tenant, "admin@" + slug + ".com");
        executive = testTenants.ensureMember(tenant, admin.id(), "exec@" + slug + ".com",
            "Executive", PASSWORD, HierarchyLevel.EXECUTIVE, null, "EXECUTIVE");
        collaborator = testTenants.ensureMember(tenant, admin.id(), "collab@" + slug + ".com",
            "Collab", PASSWORD, HierarchyLevel.COLLABORATOR, null, "COLLABORATOR");
        User otherAdmin = testTenants.admin(other, "admin@" + slug + "x.com");
        otherMember = testTenants.ensureMember(other, otherAdmin.id(),
            "member@" + slug + "x.com", "Other", PASSWORD, HierarchyLevel.COLLABORATOR,
            null, "COLLABORATOR");

        link(tenant, executive, "E-EXEC");
        link(tenant, collaborator, "E-COLLAB");
        link(other, otherMember, "E-OTHER");

        operationalLicense(tenant, executive, "ACTIVE");
        operationalLicense(tenant, collaborator, "EXPIRED");
        operationalTraining(tenant, executive, "COMPLETED", LocalDate.of(2026, 7, 10));
        operationalTraining(tenant, collaborator, "ENROLLED", null);
        operationalUsage(tenant, executive, "2026-07-15T10:00:00Z");
        operationalUsage(other, otherMember, "2026-07-15T10:00:00Z");

        historicalUsage(tenant, "E-EXEC", "2026-07-15T10:00:00");
        historicalUsage(other, "E-OTHER", "2026-07-15T10:00:00");
        historicalLicense(tenant, "E-EXEC", "ACTIVE");
        historicalLicense(tenant, "E-COLLAB", "EXPIRED");
        historicalLicense(tenant, "E-EXEC", "MYSTERY");
        historicalTraining(tenant, "E-EXEC", "COMPLETED", "2026-07-10");
        historicalTraining(tenant, "E-COLLAB", "ENROLLED", null);
        historicalTraining(tenant, "E-EXEC", "MYSTERY", null);
    }

    @Test
    void en09_operacionalUsaElegiveisUsageActivityEV023() {
        EngagementFacts facts = operationalFacts.load(tenant.id(), ATE);

        assertThat(facts.eligible()).contains(executive.id(), collaborator.id());
        assertThat(facts.activeDays().get(executive.id()))
            .containsExactly(LocalDate.of(2026, 7, 15));
        assertThat(facts.licenses().get(executive.id()))
            .singleElement()
            .satisfies(license -> assertThat(license.active()).isTrue());
        assertThat(facts.licenses().get(collaborator.id()))
            .singleElement()
            .satisfies(license -> assertThat(license.active()).isFalse());
        assertThat(facts.trainings().get(executive.id())).hasSize(1);
        assertThat(facts.coverage().eligibleTotal()).isEqualTo(2);
        assertThat(facts.coverage().eligibleLinked()).isEqualTo(2);
        assertThat(facts.coverage().unmappedLicenseStatus()).isZero();
        assertThat(facts.activeDays().get(otherMember.id())).isNull();
    }

    @Test
    void en10_historicoUsaVinculosCoberturaEMapeamentoFixo() {
        EngagementFacts facts = historicalFacts.load(tenant.id(), ATE);

        assertThat(facts.eligible()).containsExactlyInAnyOrder(executive.id(), collaborator.id());
        assertThat(facts.activeDays().get(executive.id()))
            .containsExactly(LocalDate.of(2026, 7, 15));
        assertThat(facts.activeDays()).doesNotContainKey(otherMember.id());
        assertThat(facts.licenses().get(executive.id()))
            .singleElement()
            .satisfies(license -> assertThat(license.active()).isTrue());
        assertThat(facts.licenses().get(collaborator.id()))
            .singleElement()
            .satisfies(license -> assertThat(license.active()).isFalse());
        assertThat(facts.trainings().get(executive.id())).hasSize(1);
        assertThat(facts.coverage().eligibleTotal()).isEqualTo(2);
        assertThat(facts.coverage().eligibleLinked()).isEqualTo(2);
        assertThat(facts.coverage().unmappedLicenseStatus()).isEqualTo(1);
        assertThat(facts.coverage().unmappedTrainingStatus()).isEqualTo(1);
    }

    private void link(Tenant tenant, User user, String employeeId) {
        jdbcTemplate.update("""
            INSERT INTO iam_legacy_user_links (tenant_id, user_id, legacy_employee_id)
            VALUES (?, ?, ?)
            ON CONFLICT (tenant_id, user_id) DO NOTHING
            """, tenant.id().value(), user.id().value(), employeeId);
    }

    private void operationalLicense(Tenant tenant, User user, String status) {
        jdbcTemplate.update("""
            INSERT INTO engagement_ai_licenses
                (id, tenant_id, user_id, ai_tool_id, license_type, status,
                 assigned_at, expires_at, created_at)
            VALUES (?, ?, ?, NULL, 'TEAM', ?, DATE '2026-06-01', NULL, now())
            """, UUID.randomUUID(), tenant.id().value(), user.id().value(), status);
    }

    private void operationalTraining(Tenant tenant, User user, String status,
                                     LocalDate completedAt) {
        jdbcTemplate.update("""
            INSERT INTO engagement_training_records
                (id, tenant_id, user_id, course_name, status, completed_at, created_at)
            VALUES (?, ?, ?, 'Governança', ?, ?, now())
            """, UUID.randomUUID(), tenant.id().value(), user.id().value(), status,
            completedAt == null ? null : java.sql.Date.valueOf(completedAt));
    }

    private void operationalUsage(Tenant tenant, User user, String instant) {
        jdbcTemplate.update("""
            INSERT INTO observability_usage_records
                (id, request_id, tenant_id, user_id, input_tokens, output_tokens,
                 estimated_cost, latency_ms, status, created_at)
            VALUES (?, ?, ?, ?, 1, 1, 0, 1, 'COMPLETED', ?)
            """, UUID.randomUUID(), UUID.randomUUID(), tenant.id().value(), user.id().value(),
            Timestamp.from(Instant.parse(instant)));
    }

    private void historicalUsage(Tenant tenant, String employeeId, String timestamp) {
        jdbcTemplate.update("""
            INSERT INTO historical.ai_usage_logs
                (usage_id, "timestamp", employee_id, company_id, request_count)
            VALUES (?, ?, ?, ?, 1)
            """, UUID.randomUUID().toString(), timestamp, employeeId,
            tenant.id().value().toString());
    }

    private void historicalLicense(Tenant tenant, String employeeId, String status) {
        jdbcTemplate.update("""
            INSERT INTO historical.ai_licenses
                (company_id, employee_id, ai_tool_id, license_type, assigned_date,
                 expiration_date, license_status)
            VALUES (?, ?, 'tool-1', 'TEAM', '2026-06-01', NULL, ?)
            """, tenant.id().value().toString(), employeeId, status);
    }

    private void historicalTraining(Tenant tenant, String employeeId, String status,
                                    String completedAt) {
        jdbcTemplate.update("""
            INSERT INTO historical.training_records
                (training_id, employee_id, company_id, course_name, completion_date,
                 completion_status)
            VALUES (?, ?, ?, 'Governança', ?, ?)
            """, UUID.randomUUID().toString(), employeeId, tenant.id().value().toString(),
            completedAt, status);
    }
}
