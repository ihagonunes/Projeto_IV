package br.com.qsoft.governance.agentpolicies.infrastructure.query;

import br.com.qsoft.governance.agentpolicies.application.KnowledgeHit;
import br.com.qsoft.governance.agentpolicies.application.port.KnowledgeSearchRepository;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Busca nativa com {@code websearch_to_tsquery('portuguese', …)} (ADR-35 A).
 * O tenant e a lotação são filtrados <b>antes</b> do ranking; a lotação nula
 * libera apenas documentos {@code tenant_wide}.
 */
@Component
public class KnowledgeSearchJdbcAdapter implements KnowledgeSearchRepository {

    private static final String SEARCH = """
        SELECT c.id, c.text, d.id AS document_id, d.title, v.version_number,
               ts_rank(c.search_vector, q) AS rank
          FROM agentpolicies_chunks c
          JOIN agentpolicies_document_versions v
            ON v.id = c.version_id AND v.status = 'ACTIVE'
          JOIN agentpolicies_documents d ON d.id = v.document_id
         CROSS JOIN websearch_to_tsquery('portuguese', :consulta) q
         WHERE c.tenant_id = :tenantId
           AND v.tenant_id = :tenantId
           AND d.tenant_id = :tenantId
           AND (d.tenant_wide
                OR (CAST(:departmentId AS uuid) IS NOT NULL
                    AND EXISTS (SELECT 1 FROM agentpolicies_document_scopes s
                                 WHERE s.document_id = d.id
                                   AND s.tenant_id = :tenantId
                                   AND s.department_id = CAST(:departmentId AS uuid))))
           AND c.search_vector @@ q
         ORDER BY rank DESC
         LIMIT :limit
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public KnowledgeSearchJdbcAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<KnowledgeHit> search(TenantId tenantId, UUID departmentId, String query,
                                     int limit) {
        Map<String, Object> params = new HashMap<>();
        params.put("tenantId", tenantId.value());
        params.put("departmentId", departmentId);
        params.put("consulta", query);
        params.put("limit", limit);
        return jdbcTemplate.query(SEARCH, params, (rs, rowNum) -> new KnowledgeHit(
            rs.getObject("id", UUID.class),
            rs.getString("text"),
            rs.getObject("document_id", UUID.class),
            rs.getString("title"),
            rs.getInt("version_number"),
            rs.getDouble("rank")
        ));
    }
}
