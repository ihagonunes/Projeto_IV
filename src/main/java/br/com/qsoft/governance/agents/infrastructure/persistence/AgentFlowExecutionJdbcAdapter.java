package br.com.qsoft.governance.agents.infrastructure.persistence;

import br.com.qsoft.governance.agents.application.port.AgentFlowExecutionRepository;
import br.com.qsoft.governance.agents.domain.ExecutionStatus;
import br.com.qsoft.governance.agents.domain.ExecutionStep;
import br.com.qsoft.governance.agents.domain.ExecutionStepStatus;
import br.com.qsoft.governance.agents.domain.FlowExecution;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/** Persistência tenant-scoped da execução e das etapas (V030/V031). */
@Component
public class AgentFlowExecutionJdbcAdapter implements AgentFlowExecutionRepository {

    private static final String EXECUTION_COLUMNS = """
        SELECT id, tenant_id, flow_id, flow_version_id, owner_user_id, status,
               idempotency_key, error_code, started_at, finished_at, created_at
          FROM agents_flow_executions
        """;

    private static final RowMapper<FlowExecution> EXECUTION_MAPPER = (rs, rowNum) ->
        new FlowExecution(
            rs.getObject("id", UUID.class),
            TenantId.of(rs.getObject("tenant_id", UUID.class)),
            rs.getObject("flow_id", UUID.class),
            rs.getObject("flow_version_id", UUID.class),
            UserId.of(rs.getObject("owner_user_id", UUID.class)),
            ExecutionStatus.valueOf(rs.getString("status")),
            rs.getString("idempotency_key"),
            null,
            rs.getString("error_code"),
            instantOrNull(rs, "started_at"),
            instantOrNull(rs, "finished_at"),
            rs.getTimestamp("created_at").toInstant());

    private static final RowMapper<ExecutionStep> STEP_MAPPER = (rs, rowNum) ->
        new ExecutionStep(
            rs.getObject("id", UUID.class),
            rs.getObject("execution_id", UUID.class),
            rs.getInt("position"),
            rs.getString("node_id"),
            rs.getString("node_kind"),
            ExecutionStepStatus.valueOf(rs.getString("status")),
            rs.getObject("request_id", UUID.class),
            rs.getString("error_code"),
            instantOrNull(rs, "started_at"),
            instantOrNull(rs, "finished_at"));

    private final JdbcTemplate jdbcTemplate;

    public AgentFlowExecutionJdbcAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean insertIfAbsent(FlowExecution execution) {
        return jdbcTemplate.update("""
            INSERT INTO agents_flow_executions
                (id, tenant_id, flow_id, flow_version_id, owner_user_id, status,
                 idempotency_key, error_code, started_at, finished_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """, (PreparedStatement ps) -> {
            ps.setObject(1, execution.id());
            ps.setObject(2, execution.tenantId().value());
            ps.setObject(3, execution.flowId());
            ps.setObject(4, execution.flowVersionId());
            ps.setObject(5, execution.ownerUserId().value());
            ps.setString(6, execution.status().name());
            ps.setString(7, execution.idempotencyKey());
            ps.setString(8, execution.errorCode());
            setInstant(ps, 9, execution.startedAt());
            setInstant(ps, 10, execution.finishedAt());
            ps.setTimestamp(11, Timestamp.from(execution.createdAt()));
        }) > 0;
    }

    @Override
    public Optional<FlowExecution> findByIdempotencyKey(TenantId tenantId, UserId ownerUserId,
                                                        String idempotencyKey) {
        return jdbcTemplate.query(EXECUTION_COLUMNS
                + " WHERE tenant_id = ? AND owner_user_id = ? AND idempotency_key = ?",
                EXECUTION_MAPPER, tenantId.value(), ownerUserId.value(), idempotencyKey)
            .stream()
            .findFirst();
    }

    @Override
    public Optional<FlowExecution> findById(TenantId tenantId, UUID executionId) {
        return jdbcTemplate.query(EXECUTION_COLUMNS + " WHERE tenant_id = ? AND id = ?",
                EXECUTION_MAPPER, tenantId.value(), executionId)
            .stream()
            .findFirst();
    }

    @Override
    public void insertSteps(List<ExecutionStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return;
        }
        for (ExecutionStep step : steps) {
            jdbcTemplate.update("""
                INSERT INTO agents_flow_steps
                    (id, execution_id, position, node_id, node_kind, status, request_id,
                     error_code, started_at, finished_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (PreparedStatement ps) -> {
                ps.setObject(1, step.id());
                ps.setObject(2, step.executionId());
                ps.setInt(3, step.position());
                ps.setString(4, step.nodeId());
                ps.setString(5, step.nodeKind());
                ps.setString(6, step.status().name());
                setUuid(ps, 7, step.requestId());
                ps.setString(8, step.errorCode());
                setInstant(ps, 9, step.startedAt());
                setInstant(ps, 10, step.finishedAt());
            });
        }
    }

    @Override
    public void updateStep(ExecutionStep step) {
        jdbcTemplate.update("""
            UPDATE agents_flow_steps
               SET status = ?, request_id = ?, error_code = ?, started_at = ?, finished_at = ?
             WHERE execution_id = ? AND node_id = ?
            """, (PreparedStatement ps) -> {
            ps.setString(1, step.status().name());
            setUuid(ps, 2, step.requestId());
            ps.setString(3, step.errorCode());
            setInstant(ps, 4, step.startedAt());
            setInstant(ps, 5, step.finishedAt());
            ps.setObject(6, step.executionId());
            ps.setString(7, step.nodeId());
        });
    }

    @Override
    public void finish(FlowExecution execution) {
        jdbcTemplate.update("""
            UPDATE agents_flow_executions
               SET status = ?, error_code = ?, finished_at = ?
             WHERE tenant_id = ? AND id = ?
            """, (PreparedStatement ps) -> {
            ps.setString(1, execution.status().name());
            ps.setString(2, execution.errorCode());
            setInstant(ps, 3, execution.finishedAt());
            ps.setObject(4, execution.tenantId().value());
            ps.setObject(5, execution.id());
        });
    }

    @Override
    public List<ExecutionStep> findSteps(TenantId tenantId, UUID executionId) {
        return jdbcTemplate.query("""
            SELECT s.id, s.execution_id, s.position, s.node_id, s.node_kind, s.status,
                   s.request_id, s.error_code, s.started_at, s.finished_at
              FROM agents_flow_steps s
              JOIN agents_flow_executions e ON e.id = s.execution_id
             WHERE e.tenant_id = ? AND s.execution_id = ?
             ORDER BY s.position
            """, STEP_MAPPER, tenantId.value(), executionId);
    }

    private static void setUuid(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }

    private static void setInstant(PreparedStatement ps, int index, Instant value)
        throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            ps.setTimestamp(index, Timestamp.from(value));
        }
    }

    private static Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
