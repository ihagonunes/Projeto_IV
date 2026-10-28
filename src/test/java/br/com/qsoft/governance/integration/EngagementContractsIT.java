package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.GovernanceApplication;
import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.HistoricalIdentityQuery;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.support.TestTenants;
import br.com.qsoft.governance.observability.api.query.UsageActivityQuery;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/**
 * Contratos públicos consumidos pelo engajamento (TASK-S4-E1):
 * elegíveis, vínculos históricos e dias ativos, com isolamento de tenant e
 * borda de dia UTC.
 */
@Testcontainers
@SpringBootTest(classes = GovernanceApplication.class)
class EngagementContractsIT {

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

    private static final String TENANT_SLUG = "engcontracts";
    private static final String OTHER_SLUG = "engcontractsother";
    private static final String ADMIN_EMAIL = "admin@engcontracts.com";
    private static final String EXEC_EMAIL = "exec@engcontracts.com";
    private static final String COLLAB_EMAIL = "colab@engcontracts.com";
    private static final String INACTIVE_EMAIL = "inactive@engcontracts.com";
    private static final String OTHER_ADMIN_EMAIL = "admin@engcontractsother.com";
    private static final String OTHER_MEMBER_EMAIL = "member@engcontractsother.com";
    private static final String PASSWORD = "secret123";

    @Autowired
    UserRepository userRepository;

    @Autowired
    TestTenants testTenants;

    @Autowired
    IdentityQuery identityQuery;

    @Autowired
    HistoricalIdentityQuery historicalIdentityQuery;

    @Autowired
    UsageActivityQuery usageActivityQuery;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Tenant tenant;
    private Tenant other;
    private User admin;
    private User executive;
    private User collaborator;
    private User inactive;
    private User otherMember;

    @BeforeEach
    void setUp() {
        tenant = testTenants.ensureTenant(
            TENANT_SLUG, "Engagement Contracts", "Admin", ADMIN_EMAIL, PASSWORD);
        other = testTenants.ensureTenant(
            OTHER_SLUG, "Engagement Contracts Other", "Admin", OTHER_ADMIN_EMAIL, PASSWORD);

        admin = testTenants.admin(tenant, ADMIN_EMAIL);
        UserId inviter = admin.id();
        executive = testTenants.ensureMember(tenant, inviter, EXEC_EMAIL, "Executive",
            PASSWORD, HierarchyLevel.EXECUTIVE, null, "EXECUTIVE");
        collaborator = testTenants.ensureMember(tenant, inviter, COLLAB_EMAIL, "Colab",
            PASSWORD, HierarchyLevel.COLLABORATOR, null, "COLLABORATOR");
        inactive = testTenants.ensureMember(tenant, inviter, INACTIVE_EMAIL, "Inactive",
            PASSWORD, HierarchyLevel.COLLABORATOR, null, "COLLABORATOR");
        inactive.deactivate();
        userRepository.save(inactive);

        User otherAdmin = testTenants.admin(other, OTHER_ADMIN_EMAIL);
        otherMember = testTenants.ensureMember(other, otherAdmin.id(), OTHER_MEMBER_EMAIL,
            "Other", PASSWORD, HierarchyLevel.COLLABORATOR, null, "COLLABORATOR");

        link(tenant, executive, "E-EXEC");
        link(tenant, collaborator, "E-COLLAB");
        link(other, otherMember, "E-OTHER");

        usage(tenant, executive, "2026-07-01T23:59:59Z");
        usage(tenant, executive, "2026-07-02T00:00:00Z");
        // Registro do outro tenant com o mesmo user_id: não pode vazar para o tenant A.
        usage(other, executive, "2026-07-03T12:00:00Z");
    }

    @Test
    void eligibleUsersExcludeAdminInactiveAndOtherTenant() {
        List<UserContext> eligible = identityQuery.eligibleUsers(tenant.id());

        assertThat(eligible).extracting(UserContext::userId)
            .contains(executive.id(), collaborator.id())
            .doesNotContain(admin.id(), inactive.id(), otherMember.id());
        assertThat(eligible).allMatch(context -> "ACTIVE".equals(context.status()));
        assertThat(eligible).extracting(UserContext::name)
            .contains("Executive", "Colab");
    }

    @Test
    void employeeIdsAreTenantScoped() {
        Map<UserId, String> links = historicalIdentityQuery.employeeIds(
            tenant.id(), Set.of(executive.id(), collaborator.id()));

        assertThat(links)
            .containsEntry(executive.id(), "E-EXEC")
            .containsEntry(collaborator.id(), "E-COLLAB");

        Map<UserId, String> otherLinks = historicalIdentityQuery.employeeIds(
            other.id(), Set.of(executive.id(), otherMember.id()));

        assertThat(otherLinks)
            .containsEntry(otherMember.id(), "E-OTHER")
            .doesNotContainKey(executive.id());
    }

    @Test
    void activeDaysUseUtcDayBoundary() {
        Map<UserId, Set<LocalDate>> firstDay = usageActivityQuery.activeDays(
            tenant.id(), Set.of(executive.id()),
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));

        assertThat(firstDay.get(executive.id()))
            .containsExactly(LocalDate.of(2026, 7, 1));

        Map<UserId, Set<LocalDate>> secondDay = usageActivityQuery.activeDays(
            tenant.id(), Set.of(executive.id()),
            LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 2));

        assertThat(secondDay.get(executive.id()))
            .containsExactly(LocalDate.of(2026, 7, 2));
    }

    @Test
    void activeDaysAreTenantScoped() {
        Map<UserId, Set<LocalDate>> tenantA = usageActivityQuery.activeDays(
            tenant.id(), Set.of(executive.id()),
            LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 3));

        assertThat(tenantA).doesNotContainKey(executive.id());

        Map<UserId, Set<LocalDate>> tenantB = usageActivityQuery.activeDays(
            other.id(), Set.of(executive.id()),
            LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 3));

        assertThat(tenantB.get(executive.id()))
            .containsExactly(LocalDate.of(2026, 7, 3));
    }

    private void link(Tenant tenant, User user, String employeeId) {
        jdbcTemplate.update("""
            INSERT INTO iam_legacy_user_links (tenant_id, user_id, legacy_employee_id)
            VALUES (?, ?, ?)
            ON CONFLICT (tenant_id, user_id) DO NOTHING
            """, tenant.id().value(), user.id().value(), employeeId);
    }

    private void usage(Tenant tenant, User user, String instant) {
        jdbcTemplate.update("""
            INSERT INTO observability_usage_records
                (id, request_id, tenant_id, department_id, user_id, model_id, provider_id,
                 input_tokens, output_tokens, estimated_cost, latency_ms, status, created_at)
            VALUES (?, ?, ?, NULL, ?, NULL, NULL, 1, 1, 0, 1, 'COMPLETED', ?)
            """, UUID.randomUUID(), UUID.randomUUID(), tenant.id().value(), user.id().value(),
            Timestamp.from(Instant.parse(instant)));
    }
}
