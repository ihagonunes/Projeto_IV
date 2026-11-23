package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.GovernanceApplication;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Constraints da V027 (ADR-44): status, unicidade, JSON e FK intramódulo.
 * Nenhuma tabela de execução deve existir neste incremento.
 */
@Testcontainers
@SpringBootTest(classes = GovernanceApplication.class)
class AgentsSchemaIT {

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
    JdbcTemplate jdbcTemplate;

    @Test
    void catalogAcceptsUniqueKeyVersionAndRejectsDuplicate() {
        String key = "agent_" + UUID.randomUUID();
        insertCatalog(key, 1, "ACTIVE");

        assertThatThrownBy(() -> insertCatalog(key, 1, "ACTIVE"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertCatalog(key, 2, "DRAFT")).doesNotThrowAnyException();
    }

    @Test
    void catalogRejectsStatusOutsideVocabulary() {
        assertThatThrownBy(() -> insertCatalog("agent_" + UUID.randomUUID(), 1, "ENABLED"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void overrideRequiresExistingGlobalDefinition() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
            INSERT INTO agents_catalog_overrides
                (tenant_id, agent_key, version, prompt_template, updated_at)
            VALUES (?, ?, ?, ?, now())
            """, UUID.randomUUID(), "missing_" + UUID.randomUUID(), 1, "override"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void overrideRejectsInvalidStatus() {
        String key = "agent_" + UUID.randomUUID();
        insertCatalog(key, 1, "ACTIVE");

        assertThatThrownBy(() -> jdbcTemplate.update("""
            INSERT INTO agents_catalog_overrides
                (tenant_id, agent_key, version, status, updated_at)
            VALUES (?, ?, ?, ?, now())
            """, UUID.randomUUID(), key, 1, "DELETED"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void flowRejectsInvalidStatusAndRowVersion() {
        assertThatThrownBy(() -> insertFlow(UUID.randomUUID(), UUID.randomUUID(), "DELETED", 1))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertFlow(UUID.randomUUID(), UUID.randomUUID(), "DRAFT", 0))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void publishedFlowRequiresCurrentVersion() {
        assertThatThrownBy(() -> insertFlow(UUID.randomUUID(), UUID.randomUUID(), "PUBLISHED", 1))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void flowVersionRejectsDuplicateNumberAndUnknownFlow() {
        UUID flowId = insertFlow(UUID.randomUUID(), UUID.randomUUID(), "DRAFT", 1);
        insertVersion(flowId, 1, "VALID", "{}");

        assertThatThrownBy(() -> insertVersion(flowId, 1, "VALID", "{}"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertVersion(UUID.randomUUID(), 1, "VALID", "{}"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void flowVersionRejectsInvalidValidationStatusAndNonObjectGraph() {
        UUID flowId = insertFlow(UUID.randomUUID(), UUID.randomUUID(), "DRAFT", 1);

        assertThatThrownBy(() -> insertVersion(flowId, 1, "PENDING", "{}"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertVersion(flowId, 2, "VALID", "[]"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void executionTablesExistInV030() {
        Integer tables = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM information_schema.tables
             WHERE table_schema = 'public'
               AND table_name IN ('agents_flow_executions', 'agents_flow_steps')
            """, Integer.class);

        assertThat(tables).isEqualTo(2);
    }

    private void insertCatalog(String key, int version, String status) {
        jdbcTemplate.update("""
            INSERT INTO agents_catalog
                (id, agent_key, version, name, description, category, prompt_template,
                 tools_json, knowledge_scope, model_policy, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb),
                    ?, now(), now())
            """, UUID.randomUUID(), key, version, "Nome", "Descrição", "cat", "prompt",
            "[]", "{}", "{}", status);
    }

    private UUID insertFlow(UUID tenantId, UUID ownerId, String status, long rowVersion) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO agents_flows
                (id, tenant_id, owner_user_id, name, status, row_version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, now(), now())
            """, id, tenantId, ownerId, "Fluxo", status, rowVersion);
        return id;
    }

    private void insertVersion(UUID flowId, int versionNumber, String validation, String graph) {
        jdbcTemplate.update("""
            INSERT INTO agents_flow_versions
                (id, flow_id, version_number, schema_version, graph_json, validation_status,
                 created_by, created_at)
            VALUES (?, ?, ?, 1, CAST(? AS jsonb), ?, ?, now())
            """, UUID.randomUUID(), flowId, versionNumber, graph, validation, UUID.randomUUID());
    }
}
