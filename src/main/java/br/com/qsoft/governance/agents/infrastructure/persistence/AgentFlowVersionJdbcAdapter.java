package br.com.qsoft.governance.agents.infrastructure.persistence;

import br.com.qsoft.governance.agents.application.port.AgentFlowVersionRepository;
import br.com.qsoft.governance.agents.domain.AgentFlowVersion;
import br.com.qsoft.governance.agents.domain.AgentValidationStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/**
 * Versões imutáveis do grafo (V027). {@code agents_flow_versions} não tem
 * {@code tenant_id}: leitura e escrita filtram pelo dono do fluxo via join
 * explícito. Não existe operação de alteração.
 */
@Component
public class AgentFlowVersionJdbcAdapter implements AgentFlowVersionRepository {

    private static final String SELECT = """
        SELECT v.id, v.flow_id, v.version_number, v.schema_version, v.graph_json,
               v.validation_status, v.created_by, v.created_at
          FROM agents_flow_versions v
          JOIN agents_flows f ON f.id = v.flow_id
         WHERE f.tenant_id = ?
        """;

    private static final String INSERT = """
        INSERT INTO agents_flow_versions
            (id, flow_id, version_number, schema_version, graph_json, validation_status,
             created_by, created_at)
        SELECT ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?
         WHERE EXISTS (SELECT 1 FROM agents_flows WHERE id = ? AND tenant_id = ?)
        """;

    private static final RowMapper<AgentFlowVersion> MAPPER = (rs, rowNum) -> new AgentFlowVersion(
        rs.getObject("id", UUID.class),
        rs.getObject("flow_id", UUID.class),
        rs.getInt("version_number"),
        rs.getInt("schema_version"),
        rs.getString("graph_json"),
        AgentValidationStatus.valueOf(rs.getString("validation_status")),
        UserId.of(rs.getObject("created_by", UUID.class)),
        rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public AgentFlowVersionJdbcAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AgentFlowVersion save(TenantId tenantId, AgentFlowVersion version) {
        int changed = jdbcTemplate.update(INSERT, (PreparedStatement ps) -> {
            ps.setObject(1, version.id());
            ps.setObject(2, version.flowId());
            ps.setInt(3, version.versionNumber());
            ps.setInt(4, version.schemaVersion());
            ps.setString(5, version.graphJson());
            ps.setString(6, version.validationStatus().name());
            ps.setObject(7, version.createdBy().value());
            ps.setTimestamp(8, Timestamp.from(version.createdAt()));
            ps.setObject(9, version.flowId());
            ps.setObject(10, tenantId.value());
        });
        if (changed == 0) {
            throw new IllegalStateException("Fluxo não pertence ao tenant informado.");
        }
        return version;
    }

    @Override
    public List<AgentFlowVersion> findByFlow(TenantId tenantId, UUID flowId) {
        return jdbcTemplate.query(
            SELECT + " AND v.flow_id = ? ORDER BY v.version_number DESC",
            MAPPER, tenantId.value(), flowId);
    }

    @Override
    public Optional<AgentFlowVersion> find(TenantId tenantId, UUID flowId, int versionNumber) {
        return jdbcTemplate.query(
                SELECT + " AND v.flow_id = ? AND v.version_number = ?",
                MAPPER, tenantId.value(), flowId, versionNumber)
            .stream()
            .findFirst();
    }

    @Override
    public Optional<AgentFlowVersion> findById(TenantId tenantId, UUID flowId, UUID versionId) {
        return jdbcTemplate.query(
                SELECT + " AND v.flow_id = ? AND v.id = ?",
                MAPPER, tenantId.value(), flowId, versionId)
            .stream()
            .findFirst();
    }

    @Override
    public int nextVersionNumber(TenantId tenantId, UUID flowId) {
        Integer next = jdbcTemplate.queryForObject("""
            SELECT coalesce(max(v.version_number), 0) + 1
              FROM agents_flow_versions v
              JOIN agents_flows f ON f.id = v.flow_id
             WHERE f.tenant_id = ? AND v.flow_id = ?
            """, Integer.class, tenantId.value(), flowId);
        return next == null ? 1 : next;
    }
}
