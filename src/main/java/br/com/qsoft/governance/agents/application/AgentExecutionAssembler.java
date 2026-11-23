package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.aigateway.api.model.ReceiptInfo;
import br.com.qsoft.governance.aigateway.api.query.AttemptReceiptQuery;
import br.com.qsoft.governance.agents.application.port.AgentFlowExecutionRepository;
import br.com.qsoft.governance.agents.domain.ExecutionStep;
import br.com.qsoft.governance.agents.domain.FlowExecution;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Monta a view da execução com recibos; nunca inclui prompt ou resposta de etapa. */
@Service
public class AgentExecutionAssembler {

    private final AgentFlowExecutionRepository executionRepository;
    private final AttemptReceiptQuery receiptQuery;

    public AgentExecutionAssembler(AgentFlowExecutionRepository executionRepository,
                                   AttemptReceiptQuery receiptQuery) {
        this.executionRepository = executionRepository;
        this.receiptQuery = receiptQuery;
    }

    public AgentExecutionView view(TenantId tenantId, FlowExecution execution) {
        List<ExecutionStep> steps = executionRepository.findSteps(tenantId, execution.id());
        Set<UUID> requestIds = steps.stream().map(ExecutionStep::requestId)
            .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, ReceiptInfo> receipts = requestIds.isEmpty()
            ? Map.of()
            : receiptQuery.receipts(tenantId, execution.ownerUserId(), requestIds);
        List<AgentExecutionView.StepView> views = steps.stream()
            .map(step -> new AgentExecutionView.StepView(step.nodeId(), step.nodeKind(),
                step.status(), step.requestId(), step.errorCode(), step.startedAt(),
                step.finishedAt(), step.requestId() == null ? null : receipts.get(step.requestId())))
            .toList();
        return new AgentExecutionView(execution.id(), execution.flowId(),
            execution.flowVersionId(), execution.status(), execution.result(),
            execution.errorCode(), execution.startedAt(), execution.finishedAt(), views,
            AgentExecutionView.aggregateCost(views));
    }
}
