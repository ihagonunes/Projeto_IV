package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.GovernanceApplication;
import java.sql.Connection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
 * V029: 17 definições globais (ADR-44), baseline executável em
 * `policies`/`writing`/`data`, sem `merge`/`task`/`result`, idempotente.
 */
@Testcontainers
@SpringBootTest(classes = GovernanceApplication.class)
class AgentsCatalogSeedIT {

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
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void seedCreatesSeventeenDefinitionsWithBaselineStatus() {
        assertThat(agentKeys()).hasSize(17).doesNotContain("merge", "task", "result");
        assertThat(agentKeys()).contains("policies", "writing", "data", "code", "tests", "finance",
            "ai-cost", "sales", "marketing", "support", "stock", "operations", "quality",
            "people", "projects", "research", "review");

        List<String> active = jdbcTemplate.queryForList(
            "SELECT agent_key FROM agents_catalog WHERE status = 'ACTIVE' ORDER BY agent_key",
            String.class);
        assertThat(active).containsExactly("data", "policies", "projects", "research",
            "review", "writing");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM agents_catalog WHERE status = 'DRAFT'", Integer.class)).isEqualTo(11);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM agents_catalog WHERE version <> 1", Integer.class)).isZero();
    }

    @Test
    void seedUsesStableIds() {
        assertThat(jdbcTemplate.queryForObject(
            "SELECT id FROM agents_catalog WHERE agent_key = 'policies'", UUID.class))
            .hasToString("5f0c1a00-0000-4000-8000-000000000001");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT id FROM agents_catalog WHERE agent_key = 'review'", UUID.class))
            .hasToString("5f0c1a00-0000-4000-8000-000000000011");
    }

    @Test
    void seedFillsReviewablePoliciesWithoutTools() {
        Integer invalid = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM agents_catalog
             WHERE btrim(prompt_template) = ''
                OR tools_json <> '[]'::jsonb
                OR knowledge_scope ->> 'mode' IS DISTINCT FROM 'NONE'
                OR knowledge_scope ? 'topics'
                OR model_policy ->> 'mode' IS DISTINCT FROM 'INHERIT'
                OR btrim(name) = '' OR btrim(category) = ''
            """, Integer.class);

        assertThat(invalid).isZero();
    }

    @Test
    void seedIsIdempotent() throws Exception {
        runSeed();
        runSeed();

        assertThat(agentKeys()).hasSize(17);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM agents_catalog", Integer.class)).isEqualTo(17);
    }

    private void runSeed() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                new ClassPathResource("db/migration/V029__agents_catalog_seed.sql"));
        }
    }

    private Set<String> agentKeys() {
        return Set.copyOf(jdbcTemplate.queryForList(
            "SELECT agent_key FROM agents_catalog", String.class));
    }
}
