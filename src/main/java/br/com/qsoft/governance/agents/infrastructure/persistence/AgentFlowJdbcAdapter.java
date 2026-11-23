package br.com.qsoft.governance.agents.infrastructure.persistence;

import br.com.qsoft.governance.agents.application.port.AgentFlowRepository;
import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.agents.domain.AgentFlowStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/**
 * Persistência de fluxos (V027). Leituras filtram {@code tenant_id} e ocultam
 * arquivados; o upsert nunca altera linha de outro tenant.
 */
@Component
public class AgentFlowJdbcAdapter implements AgentFlowRepository {

    private static final String SELECT = """
        SELECT id, tenant_id, owner_user_id, department_id, name, status,
               current_version_id, row_version, created_at, updated_at, deleted_at
          FROM agents_flows
        """;

    private static final String UPSERT = """
        INSERT INTO agents_flows
            (id, tenant_id, owner_user_id, department_id, name, status, current_version_id,
             row_version, created_at, updated_at, deleted_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE
           SET owner_user_id = EXCLUDED.owner_user_id,
               department_id = EXCLUDED.department_id,
               name = EXCLUDED.name,
               status = EXCLUDED.status,
               current_version_id = EXCLUDED.current_version_id,
               row_version = EXCLUDED.row_version,
               updated_at = EXCLUDED.updated_at,
               deleted_at = EXCLUDED.deleted_at
         WHERE agents_flows.tenant_id = EXCLUDED.tenant_id
        """;

    private static final RowMapper<AgentFlow> MAPPER = (rs, rowNum) -> new AgentFlow(
        rs.getObject("id", UUID.class),
        TenantId.of(rs.getObject("tenant_id", UUID.class)),
        UserId.of(rs.getObject("owner_user_id", UUID.class)),
        rs.getObject("department_id", UUID.class),
        rs.getString("name"),
        AgentFlowStatus.valueOf(rs.getString("status")),
        rs.getObject("current_version_id", UUID.class),
        rs.getLong("row_version"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"),
        instantOrNull(rs, "deleted_at"));

    private final JdbcTemplate jdbcTemplate;

    public AgentFlowJdbcAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AgentFlow save(AgentFlow flow) {
        int changed = jdbcTemplate.update(UPSERT, (PreparedStatement ps) -> {
            ps.setObject(1, flow.id());
            ps.setObject(2, flow.tenantId().value());
            ps.setObject(3, flow.ownerUserId().value());
            setUuid(ps, 4, flow.departmentId());
            ps.setString(5, flow.name());
            ps.setString(6, flow.status().name());
            setUuid(ps, 7, flow.currentVersionId());
            ps.setLong(8, flow.rowVersion());
            ps.setTimestamp(9, Timestamp.from(flow.createdAt()));
            ps.setTimestamp(10, Timestamp.from(flow.updatedAt()));
            setInstant(ps, 11, flow.deletedAt());
        });
        if (changed == 0) {
            throw new IllegalStateException("Fluxo pertence a outro tenant.");
        }
        return flow;
    }

    @Override
    public Optional<AgentFlow> findById(TenantId tenantId, UUID flowId) {
        return jdbcTemplate.query(
                SELECT + " WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL",
                MAPPER, tenantId.value(), flowId)
            .stream()
            .findFirst();
    }

    @Override
    public List<AgentFlow> findByOwner(TenantId tenantId, UserId ownerUserId) {
        return jdbcTemplate.query(
            SELECT + " WHERE tenant_id = ? AND owner_user_id = ? AND deleted_at IS NULL"
                + " ORDER BY updated_at DESC, id DESC",
            MAPPER, tenantId.value(), ownerUserId.value());
    }

    @Override
    public List<AgentFlow> findByDepartment(TenantId tenantId, UUID departmentId) {
        return jdbcTemplate.query(
            SELECT + " WHERE tenant_id = ? AND department_id = ? AND deleted_at IS NULL"
                + " ORDER BY updated_at DESC, id DESC",
            MAPPER, tenantId.value(), departmentId);
    }

    @Override
    public PageResult<AgentFlow> findVisible(TenantId tenantId, VisibilityScope scope,
                                             AgentFlowStatus status, int page, int size) {
        if (scope instanceof VisibilityScope.None) {
            return new PageResult<>(List.of(), 0, page, size);
        }
        StringBuilder where = new StringBuilder(
            " WHERE tenant_id = ? AND deleted_at IS NULL");
        List<Object> args = new ArrayList<>();
        args.add(tenantId.value());

        if (scope instanceof VisibilityScope.SelfOnly self) {
            where.append(" AND owner_user_id = ?");
            args.add(self.self().value());
        } else if (scope instanceof VisibilityScope.Departments departments) {
            where.append(" AND (owner_user_id = ?");
            args.add(departments.self().value());
            if (!departments.ids().isEmpty()) {
                where.append(" OR department_id IN (")
                    .append(String.join(",", Collections.nCopies(departments.ids().size(), "?")))
                    .append(")");
                args.addAll(departments.ids());
            }
            where.append(")");
        }
        if (status != null) {
            where.append(" AND status = ?");
            args.add(status.name());
        }

        Long total = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM agents_flows" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<AgentFlow> content = jdbcTemplate.query(
            SELECT + where + " ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?",
            MAPPER, pageArgs.toArray());
        return new PageResult<>(content, total == null ? 0 : total, page, size);
    }

    @Override
    public boolean updateIfRowVersion(AgentFlow flow, long expectedRowVersion) {
        return jdbcTemplate.update("""
            UPDATE agents_flows
               SET name = ?,
                   status = ?,
                   current_version_id = ?,
                   row_version = row_version + 1,
                   updated_at = ?
             WHERE tenant_id = ? AND id = ? AND row_version = ? AND deleted_at IS NULL
            """, (PreparedStatement ps) -> {
            ps.setString(1, flow.name());
            ps.setString(2, flow.status().name());
            setUuid(ps, 3, flow.currentVersionId());
            ps.setTimestamp(4, Timestamp.from(flow.updatedAt()));
            ps.setObject(5, flow.tenantId().value());
            ps.setObject(6, flow.id());
            ps.setLong(7, expectedRowVersion);
        }) > 0;
    }

    @Override
    public boolean markDeleted(TenantId tenantId, UUID flowId, Instant deletedAt) {
        return jdbcTemplate.update("""
            UPDATE agents_flows
               SET deleted_at = ?,
                   updated_at = ?,
                   row_version = row_version + 1
             WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL
            """, Timestamp.from(deletedAt), Timestamp.from(deletedAt),
            tenantId.value(), flowId) > 0;
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

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private static Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
