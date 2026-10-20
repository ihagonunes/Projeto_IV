package br.com.qsoft.governance.agentpolicies.application.port;

import br.com.qsoft.governance.agentpolicies.application.KnowledgeHit;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.List;
import java.util.UUID;

/**
 * Recuperação nativa e segura (ADR-14/15). O filtro de tenant e de lotação é
 * aplicado <b>antes</b> do ranking.
 */
public interface KnowledgeSearchRepository {

    List<KnowledgeHit> search(TenantId tenantId, UUID departmentId, String query, int limit);
}
