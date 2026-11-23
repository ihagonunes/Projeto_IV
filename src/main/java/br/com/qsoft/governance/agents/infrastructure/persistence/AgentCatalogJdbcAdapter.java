package br.com.qsoft.governance.agents.infrastructure.persistence;

import br.com.qsoft.governance.agents.application.port.AgentCatalogRepository;
import br.com.qsoft.governance.agents.domain.AgentDefinition;
import br.com.qsoft.governance.agents.domain.AgentDefinitionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/** Leitura do catálogo global (V027). Não há escrita pela aplicação. */
@Component
public class AgentCatalogJdbcAdapter implements AgentCatalogRepository {

    private static final String SELECT = """
        SELECT id, agent_key, version, name, description, category, prompt_template,
               tools_json, knowledge_scope, model_policy, status, created_at, updated_at
          FROM agents_catalog
        """;

    private static final RowMapper<AgentDefinition> MAPPER = (rs, rowNum) -> new AgentDefinition(
        rs.getObject("id", UUID.class),
        rs.getString("agent_key"),
        rs.getInt("version"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getString("category"),
        rs.getString("prompt_template"),
        rs.getString("tools_json"),
        rs.getString("knowledge_scope"),
        rs.getString("model_policy"),
        AgentDefinitionStatus.valueOf(rs.getString("status")),
        instant(rs, "created_at"),
        instant(rs, "updated_at"));

    private final JdbcTemplate jdbcTemplate;

    public AgentCatalogJdbcAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<AgentDefinition> findAll() {
        return jdbcTemplate.query(SELECT + " ORDER BY agent_key, version DESC", MAPPER);
    }

    @Override
    public Optional<AgentDefinition> findByAgentKey(String agentKey, int version) {
        return jdbcTemplate.query(
                SELECT + " WHERE agent_key = ? AND version = ?", MAPPER, agentKey, version)
            .stream()
            .findFirst();
    }

    @Override
    public boolean existsByAgentKey(String agentKey) {
        Boolean exists = jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM agents_catalog WHERE agent_key = ?)",
            Boolean.class, agentKey);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public Optional<AgentDefinition> findLatestByAgentKey(String agentKey) {
        return jdbcTemplate.query(
                SELECT + " WHERE agent_key = ? ORDER BY version DESC LIMIT 1", MAPPER, agentKey)
            .stream()
            .findFirst();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }
}
