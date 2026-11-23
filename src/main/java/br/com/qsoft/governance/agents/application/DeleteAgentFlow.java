package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.agents.application.port.AgentFlowRepository;
import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Arquivamento lógico do fluxo (é `deleted_at`, nunca DELETE físico). */
@Service
public class DeleteAgentFlow {

    private final AgentFlowRepository flowRepository;
    private final AuthorizationQuery authorizationQuery;

    public DeleteAgentFlow(AgentFlowRepository flowRepository,
                           AuthorizationQuery authorizationQuery) {
        this.flowRepository = flowRepository;
        this.authorizationQuery = authorizationQuery;
    }

    @Transactional
    public void delete(TenantId tenantId, UserId actorUserId, UUID flowId) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
        Objects.requireNonNull(actorUserId, "actorUserId é obrigatório.");
        Objects.requireNonNull(flowId, "flowId é obrigatório.");

        AgentFlow flow = flowRepository.findById(tenantId, flowId)
            .orElseThrow(AgentFlowNotFoundException::new);
        if (!AgentFlowAccess.visible(authorizationQuery.visibilityFor(
                tenantId, actorUserId, Resource.AGENT, Action.MANAGE), flow)) {
            throw new AgentFlowNotFoundException();
        }
        if (!flowRepository.markDeleted(tenantId, flowId, Instant.now())) {
            throw new AgentFlowNotFoundException();
        }
    }
}
