package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.GovernanceApplication;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.support.TestTenants;
import java.sql.Connection;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * V028 no cenário real: um tenant já existente recebe AGENT:* sem perder
 * nenhuma permissão anterior; executar a migration duas vezes não duplica
 * linhas. Em banco novo a V028 roda antes de qualquer tenant, então a
 * distribuição normal acontece pelo {@code SystemRole}.
 */
@Testcontainers
@SpringBootTest(classes = GovernanceApplication.class)
class AgentsRbacMigrationIT {

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

    private static final String SLUG = "agentsv028";
    private static final String ADMIN_EMAIL = "admin@agentsv028.com";
    private static final String PASSWORD = "secret123";

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TestTenants testTenants;

    @Test
    void migrationAlignsExistingTenantIdempotently() throws Exception {
        Tenant tenant = testTenants.ensureTenant(SLUG, "Agents V028", "Admin",
            ADMIN_EMAIL, PASSWORD);

        // Simula o estado anterior à V028: o tenant não tem AGENT:*.
        jdbcTemplate.update("""
            DELETE FROM iam_role_permissions p
             USING iam_roles r
             WHERE p.role_id = r.id
               AND r.tenant_id = ?
               AND p.resource = 'AGENT'
            """, tenant.id().value());
        int baselineOtherPermissions = countNonAgentPermissions(tenant);
        assertThat(agentPermissions(tenant)).isEmpty();

        runV028();
        runV028();

        assertThat(agentPermissions(tenant)).hasSize(13).contains(
            "COLLABORATOR:READ", "COLLABORATOR:RUN",
            "LEADER:READ", "LEADER:MANAGE", "LEADER:RUN",
            "MANAGER:READ", "MANAGER:MANAGE", "MANAGER:RUN",
            "EXECUTIVE:READ", "EXECUTIVE:RUN",
            "TENANT_ADMIN:READ", "TENANT_ADMIN:MANAGE", "TENANT_ADMIN:RUN");
        assertThat(agentPermissions(tenant)).doesNotContain(
            "COLLABORATOR:MANAGE", "EXECUTIVE:MANAGE");
        assertThat(countNonAgentPermissions(tenant)).isEqualTo(baselineOtherPermissions);
    }

    private void runV028() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                new ClassPathResource("db/migration/V028__agents_rbac.sql"));
        }
    }

    private Set<String> agentPermissions(Tenant tenant) {
        Set<String> permissions = new HashSet<>(jdbcTemplate.query("""
            SELECT r.name || ':' || p.action
              FROM iam_role_permissions p
              JOIN iam_roles r ON r.id = p.role_id
             WHERE r.tenant_id = ? AND p.resource = 'AGENT'
            """, (rs, rowNum) -> rs.getString(1), tenant.id().value()));
        return permissions;
    }

    private int countNonAgentPermissions(Tenant tenant) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT count(*)
              FROM iam_role_permissions p
              JOIN iam_roles r ON r.id = p.role_id
             WHERE r.tenant_id = ? AND p.resource <> 'AGENT'
            """, Integer.class, tenant.id().value());
        return count == null ? 0 : count;
    }
}
