package br.com.qsoft.governance.agentpolicies.infrastructure.persistence;

import br.com.qsoft.governance.agentpolicies.application.port.AgentExecutionRepository;
import br.com.qsoft.governance.agentpolicies.domain.AgentExecution;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Upsert da execução; arrays nativos (uuid[], varchar[]) via JDBC. */
@Component
public class AgentExecutionRepositoryAdapter implements AgentExecutionRepository {

    private static final String UPSERT = """
        INSERT INTO agentpolicies_executions
            (id, tenant_id, user_id, request_id, status, document_ids, tools_called,
             created_at, finished_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            request_id    = EXCLUDED.request_id,
            status        = EXCLUDED.status,
            document_ids  = EXCLUDED.document_ids,
            tools_called  = EXCLUDED.tools_called,
            finished_at   = EXCLUDED.finished_at
        """;

    private final JdbcTemplate jdbcTemplate;

    public AgentExecutionRepositoryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(AgentExecution execution) {
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(UPSERT);
            statement.setObject(1, execution.id());
            statement.setObject(2, execution.tenantId().value());
            statement.setObject(3, execution.userId().value());
            if (execution.requestId() == null) {
                statement.setNull(4, Types.OTHER);
            } else {
                statement.setObject(4, execution.requestId());
            }
            statement.setString(5, execution.status().name());
            statement.setArray(6, connection.createArrayOf("uuid",
                execution.documentIds().toArray()));
            statement.setArray(7, connection.createArrayOf("varchar",
                execution.toolsCalled().toArray()));
            statement.setObject(8, OffsetDateTime.ofInstant(execution.createdAt(),
                ZoneOffset.UTC));
            if (execution.finishedAt() == null) {
                statement.setNull(9, Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                statement.setObject(9, OffsetDateTime.ofInstant(execution.finishedAt(),
                    ZoneOffset.UTC));
            }
            return statement;
        });
    }
}
