package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.GovernanceApplication;
import java.util.List;
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

/** V030: unicidade, status, FK e ausência de prompt/resposta nas tabelas. */
@Testcontainers
@SpringBootTest(classes = GovernanceApplication.class)
class AgentsExecutionSchemaIT {

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
    void executionRequiresKnownFlowAndVersion() {
        UUID tenant = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        assertThatThrownBy(() -> insertExecution(tenant, owner, UUID.randomUUID(),
            UUID.randomUUID(), "idem-1", "RUNNING"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void idempotencyKeyIsUniquePerTenantAndOwner() {
        UUID tenant = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID flow = insertFlow(tenant, owner);
        UUID version = insertVersion(flow, 1);
        insertExecution(tenant, owner, flow, version, "same-key", "RUNNING");

        assertThatThrownBy(() -> insertExecution(tenant, owner, flow, version, "same-key",
            "RUNNING"))
            .isInstanceOf(DataIntegrityViolationException.class);

        UUID otherOwner = UUID.randomUUID();
        insertExecution(tenant, otherOwner, flow, version, "same-key", "RUNNING");
    }

    @Test
    void executionAndStepStatusesAreClosed() {
        UUID tenant = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID flow = insertFlow(tenant, owner);
        UUID version = insertVersion(flow, 1);
        UUID execution = insertExecution(tenant, owner, flow, version, "idem-2", "RUNNING");

        assertThatThrownBy(() -> insertExecution(tenant, owner, flow, version, "idem-3", "DONE"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertStep(execution, "n1", "BROKEN"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stepsAreUniquePerExecutionAndNode() {
        UUID tenant = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID flow = insertFlow(tenant, owner);
        UUID version = insertVersion(flow, 1);
        UUID execution = insertExecution(tenant, owner, flow, version, "idem-4", "RUNNING");
        insertStep(execution, "node-1", "PENDING");

        assertThatThrownBy(() -> insertStep(execution, "node-1", "PENDING"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void executionTablesDoNotStorePromptOrResponse() {
        List<String> forbidden = List.of("prompt", "question", "pergunta", "answer", "resposta",
            "response", "content", "message");
        for (String table : List.of("agents_flow_executions", "agents_flow_steps")) {
            List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = ?",
                String.class, table);
            assertThat(columns).noneMatch(column -> forbidden.contains(column.toLowerCase()));
        }
    }

    private UUID insertFlow(UUID tenantId, UUID owner) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO agents_flows (id, tenant_id, owner_user_id, name, status, row_version,
                created_at, updated_at)
            VALUES (?, ?, ?, 'Fluxo', 'DRAFT', 1, now(), now())
            """, id, tenantId, owner);
        return id;
    }

    private UUID insertVersion(UUID flowId, int number) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO agents_flow_versions
                (id, flow_id, version_number, schema_version, graph_json, validation_status,
                 created_by, created_at)
            VALUES (?, ?, ?, 1, '{"schemaVersion":1,"nodes":[],"edges":[]}'::jsonb, 'VALID',
                    ?, now())
            """, id, flowId, number, UUID.randomUUID());
        return id;
    }

    private UUID insertExecution(UUID tenantId, UUID owner, UUID flowId, UUID version,
                                 String key, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO agents_flow_executions
                (id, tenant_id, flow_id, flow_version_id, owner_user_id, status,
                 idempotency_key, started_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())
            """, id, tenantId, flowId, version, owner, status, key);
        return id;
    }

    private void insertStep(UUID executionId, String nodeId, String status) {
        jdbcTemplate.update("""
            INSERT INTO agents_flow_steps
                (id, execution_id, position, node_id, node_kind, status)
            VALUES (?, ?, 0, ?, 'agent', ?)
            """, UUID.randomUUID(), executionId, nodeId, status);
    }
}
