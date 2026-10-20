package br.com.qsoft.governance.agentpolicies.application;

import br.com.qsoft.governance.agentpolicies.application.port.KnowledgeSearchRepository;
import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Recuperação segura de conhecimento (ADR-14/15/35). A lotação do usuário vem
 * do {@link IdentityQuery}, nunca do cliente; sem lotação, apenas documentos
 * {@code tenant_wide} são retornados.
 */
@Service
public class KnowledgeAccessService {

    static final int LIMIT = 5;

    private final KnowledgeSearchRepository searchRepository;
    private final IdentityQuery identityQuery;

    public KnowledgeAccessService(KnowledgeSearchRepository searchRepository,
                                  IdentityQuery identityQuery) {
        this.searchRepository = searchRepository;
        this.identityQuery = identityQuery;
    }

    public List<KnowledgeHit> search(TenantId tenantId, UserId userId, String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        UUID departmentId = identityQuery.getUserContext(userId)
            .filter(context -> context.tenantId().equals(tenantId))
            .map(UserContext::departmentId)
            .orElse(null);
        return searchRepository.search(tenantId, departmentId, query, LIMIT);
    }
}
