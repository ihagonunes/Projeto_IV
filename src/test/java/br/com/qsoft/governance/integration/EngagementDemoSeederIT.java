package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.GovernanceApplication;
import br.com.qsoft.governance.engagement.application.AdoptionCommand;
import br.com.qsoft.governance.engagement.application.QueryTrainingCross;
import br.com.qsoft.governance.engagement.application.TrainingCrossReport;
import br.com.qsoft.governance.engagement.domain.SourceKind;
import br.com.qsoft.governance.engagement.infrastructure.bootstrap.EngagementDemoSeeder;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.support.TestTenants;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Seeder de engajamento: idempotente e popula os quatro quadrantes. */
@Testcontainers
@SpringBootTest(classes = GovernanceApplication.class,
    properties = {
        "app.bootstrap.demo-data=true",
        "app.bootstrap.demo-password=secret123"
    })
class EngagementDemoSeederIT {

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

    @Autowired
    TestTenants testTenants;

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    IdentityQuery identityQuery;

    @Autowired
    EngagementDemoSeeder seeder;

    @Autowired
    QueryTrainingCross queryTrainingCross;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Tenant tenant;
    private User executive;

    @BeforeEach
    void setUp() {
        tenant = tenantRepository.findBySlug("qsoft").orElseThrow();
        User admin = testTenants.admin(tenant, "admin@qsoft.com");
        executive = testTenants.ensureMember(tenant, admin.id(), "exec.demo@qsoft.com",
            "Member 0", "secret123", HierarchyLevel.EXECUTIVE, null, "EXECUTIVE");
        User member1 = testTenants.ensureMember(tenant, admin.id(), "member1.demo@qsoft.com",
            "Member 1", "secret123", HierarchyLevel.COLLABORATOR, null, "COLLABORATOR");
        testTenants.ensureMember(tenant, admin.id(), "member2.demo@qsoft.com",
            "Member 2", "secret123", HierarchyLevel.COLLABORATOR, null, "COLLABORATOR");
        testTenants.ensureMember(tenant, admin.id(), "member3.demo@qsoft.com",
            "Member 3", "secret123", HierarchyLevel.COLLABORATOR, null, "COLLABORATOR");
        usage(executive);
        usage(member1);
        assertThat(identityQuery.eligibleUsers(tenant.id())).hasSize(4);
    }

    @Test
    void rodaDuasVezesSemDuplicarEPopulaOsQuatroQuadrantes() {
        seeder.run(null);
        int licenses = count("engagement_ai_licenses");
        int trainings = count("engagement_training_records");
        assertThat(licenses).isEqualTo(2);
        assertThat(trainings).isEqualTo(4);

        seeder.run(null);
        assertThat(count("engagement_ai_licenses")).isEqualTo(licenses);
        assertThat(count("engagement_training_records")).isEqualTo(trainings);

        TenantContextHolder.set(new TenantContext(tenant.id(), executive.id()));
        try {
            TrainingCrossReport report = queryTrainingCross.query(new AdoptionCommand(
                tenant.id(), executive.id(), SourceKind.OPERACIONAL,
                LocalDate.now(ZoneOffset.UTC), null));
            assertThat(report.trainedActive().total()).isEqualTo(1);
            assertThat(report.trainedInactive().total()).isEqualTo(1);
            assertThat(report.notTrainedActive().total()).isEqualTo(1);
            assertThat(report.notTrainedInactive().total()).isEqualTo(1);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM " + table + " WHERE tenant_id = ?",
            Integer.class, tenant.id().value());
    }

    private void usage(User user) {
        jdbcTemplate.update("""
            INSERT INTO observability_usage_records
                (id, request_id, tenant_id, user_id, input_tokens, output_tokens,
                 estimated_cost, latency_ms, status, created_at)
            VALUES (?, ?, ?, ?, 1, 1, 0, 1, 'COMPLETED', ?)
            """, UUID.randomUUID(), UUID.randomUUID(), tenant.id().value(), user.id().value(),
            Timestamp.from(Instant.now()));
    }
}
