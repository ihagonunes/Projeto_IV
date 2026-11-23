package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.agents.application.port.AgentFlowRepository;
import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.agents.domain.AgentFlowStatus;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lista paginada dos fluxos visíveis ao ator (ADR-28/ADR-44). O escopo é
 * resolvido no backend por `visibilityFor(AGENT:READ)`; `DELETED` nunca aparece.
 */
@Service
public class ListAgentFlows {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private final AgentFlowRepository flowRepository;
    private final AuthorizationQuery authorizationQuery;

    public ListAgentFlows(AgentFlowRepository flowRepository, AuthorizationQuery authorizationQuery) {
        this.flowRepository = flowRepository;
        this.authorizationQuery = authorizationQuery;
    }

    @Transactional(readOnly = true)
    public PageResult<AgentFlow> list(TenantId tenantId, UserId actorUserId,
                                      AgentFlowStatus status, int page, int size) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
        Objects.requireNonNull(actorUserId, "actorUserId é obrigatório.");

        VisibilityScope scope = authorizationQuery.visibilityFor(
            tenantId, actorUserId, Resource.AGENT, Action.READ);
        if (scope instanceof VisibilityScope.None) {
            throw new AgentFlowAccessDeniedException();
        }
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return flowRepository.findVisible(tenantId, scope, status, safePage, safeSize);
    }
}
