package br.com.qsoft.governance.agents.infrastructure.persistence;

import br.com.qsoft.governance.agents.application.port.AgentCatalogOverrideRepository;
import br.com.qsoft.governance.agents.domain.AgentDefinitionOverride;
import br.com.qsoft.governance.agents.domain.AgentDefinitionStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
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

/** Overrides do catálogo por tenant (V027), sempre filtrados por {@code tenant_id}. */
@Component
public class AgentCatalogOverrideJdbcAdapter implements AgentCatalogOverrideRepository {

    private static final String SELECT = """
        SELECT tenant_id, agent_key, version, prompt_template, tools_json,
               knowledge_scope, model_policy, status, updated_at
          FROM agents_catalog_overrides
        """;

    private static final String UPSERT = """
        INSERT INTO agents_catalog_overrides
            (tenant_id, agent_key, version, prompt_template, tools_json, knowledge_scope,
             model_policy, status, updated_at)
        VALUES (?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb), ?, ?)
        ON CONFLICT (tenant_id, agent_key, version) DO UPDATE
           SET prompt_template = EXCLUDED.prompt_template,
               tools_json = EXCLUDED.tools_json,
               knowledge_scope = EXCLUDED.knowledge_scope,
               model_policy = EXCLUDED.model_policy,
               status = EXCLUDED.status,
               updated_at = EXCLUDED.updated_at
        """;

    private static final RowMapper<AgentDefinitionOverride> MAPPER = (rs, rowNum) ->
        new AgentDefinitionOverride(
            TenantId.of(rs.getObject("tenant_id", UUID.class)),
            rs.getString("agent_key"),
            rs.getInt("version"),
            rs.getString("prompt_template"),
            rs.getString("tools_json"),
            rs.getString("knowledge_scope"),
            rs.getString("model_policy"),
            status(rs.getString("status")),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public AgentCatalogOverrideJdbcAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<AgentDefinitionOverride> findByTenant(TenantId tenantId) {
        return jdbcTemplate.query(
            SELECT + " WHERE tenant_id = ? ORDER BY agent_key, version", MAPPER, tenantId.value());
    }

    @Override
    public Optional<AgentDefinitionOverride> find(TenantId tenantId, String agentKey, int version) {
        return jdbcTemplate.query(
                SELECT + " WHERE tenant_id = ? AND agent_key = ? AND version = ?",
                MAPPER, tenantId.value(), agentKey, version)
            .stream()
            .findFirst();
    }

    @Override
    public void save(AgentDefinitionOverride override) {
        jdbcTemplate.update(UPSERT, (PreparedStatement ps) -> {
            ps.setObject(1, override.tenantId().value());
            ps.setString(2, override.agentKey());
            ps.setInt(3, override.version());
            setText(ps, 4, override.promptTemplate());
            setText(ps, 5, override.toolsJson());
            setText(ps, 6, override.knowledgeScopeJson());
            setText(ps, 7, override.modelPolicyJson());
            if (override.status() == null) {
                ps.setNull(8, Types.VARCHAR);
            } else {
                ps.setString(8, override.status().name());
            }
            ps.setTimestamp(9, Timestamp.from(override.updatedAt()));
        });
    }

    @Override
    public boolean delete(TenantId tenantId, String agentKey, int version) {
        return jdbcTemplate.update("""
            DELETE FROM agents_catalog_overrides
             WHERE tenant_id = ? AND agent_key = ? AND version = ?
            """, tenantId.value(), agentKey, version) > 0;
    }

    @Override
    public int deleteByTenantAndAgentKey(TenantId tenantId, String agentKey) {
        return jdbcTemplate.update("""
            DELETE FROM agents_catalog_overrides
             WHERE tenant_id = ? AND agent_key = ?
            """, tenantId.value(), agentKey);
    }

    private static AgentDefinitionStatus status(String raw) {
        return raw == null ? null : AgentDefinitionStatus.valueOf(raw);
    }

    private static void setText(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }
}
