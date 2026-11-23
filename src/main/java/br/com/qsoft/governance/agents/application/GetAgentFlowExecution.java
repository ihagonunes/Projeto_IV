package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.agents.application.port.AgentFlowExecutionRepository;
import br.com.qsoft.governance.agents.application.port.AgentFlowRepository;
import br.com.qsoft.governance.agents.domain.FlowExecution;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta uma execução; dono ou quem enxerga o fluxo pelo `AGENT:READ`. */
@Service
public class GetAgentFlowExecution {

    private final AgentFlowExecutionRepository executionRepository;
    private final AgentFlowRepository flowRepository;
    private final AuthorizationQuery authorizationQuery;
    private final AgentExecutionAssembler assembler;

    public GetAgentFlowExecution(AgentFlowExecutionRepository executionRepository,
                                 AgentFlowRepository flowRepository,
                                 AuthorizationQuery authorizationQuery,
                                 AgentExecutionAssembler assembler) {
        this.executionRepository = executionRepository;
        this.flowRepository = flowRepository;
        this.authorizationQuery = authorizationQuery;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public AgentExecutionView get(TenantId tenantId, UserId actorUserId, UUID executionId) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
        Objects.requireNonNull(actorUserId, "actorUserId é obrigatório.");
        Objects.requireNonNull(executionId, "executionId é obrigatório.");

        FlowExecution execution = executionRepository.findById(tenantId, executionId)
            .orElseThrow(AgentExecutionNotFoundException::new);
        if (!execution.ownerUserId().equals(actorUserId)) {
            var flow = flowRepository.findById(tenantId, execution.flowId()).orElse(null);
            if (flow == null || !AgentFlowAccess.visible(authorizationQuery.visibilityFor(
                    tenantId, actorUserId, Resource.AGENT, Action.READ), flow)) {
                throw new AgentExecutionNotFoundException();
            }
        }
        return assembler.view(tenantId, execution);
    }
}
