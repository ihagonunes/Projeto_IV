package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.agents.application.port.AgentFlowRepository;
import br.com.qsoft.governance.agents.application.port.AgentFlowVersionRepository;
import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Carrega um fluxo visível com a versão corrente e o grafo (ADR-44). */
@Service
public class GetAgentFlow {

    private final AgentFlowRepository flowRepository;
    private final AgentFlowVersionRepository versionRepository;
    private final AuthorizationQuery authorizationQuery;

    public GetAgentFlow(AgentFlowRepository flowRepository,
                        AgentFlowVersionRepository versionRepository,
                        AuthorizationQuery authorizationQuery) {
        this.flowRepository = flowRepository;
        this.versionRepository = versionRepository;
        this.authorizationQuery = authorizationQuery;
    }

    @Transactional(readOnly = true)
    public AgentFlowDetail get(TenantId tenantId, UserId actorUserId, UUID flowId) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
        Objects.requireNonNull(actorUserId, "actorUserId é obrigatório.");
        Objects.requireNonNull(flowId, "flowId é obrigatório.");

        AgentFlow flow = flowRepository.findById(tenantId, flowId)
            .orElseThrow(AgentFlowNotFoundException::new);
        if (!AgentFlowAccess.visible(authorizationQuery.visibilityFor(
                tenantId, actorUserId, Resource.AGENT, Action.READ), flow)) {
            throw new AgentFlowNotFoundException();
        }
        var version = flow.currentVersionId() == null
            ? null
            : versionRepository.findById(tenantId, flow.id(), flow.currentVersionId())
                .orElse(null);
        return new AgentFlowDetail(flow, version);
    }
}
