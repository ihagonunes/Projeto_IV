package br.com.qsoft.governance.agents.infrastructure.web;

import br.com.qsoft.governance.agents.application.AgentExecutionView;
import br.com.qsoft.governance.agents.domain.ExecutionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Execução + etapas + recibos + custo agregado. */
public record AgentExecutionResponse(
    UUID id,
    UUID flowId,
    UUID flowVersionId,
    ExecutionStatus status,
    String result,
    String errorCode,
    Instant startedAt,
    Instant finishedAt,
    List<ExecutionStepResponse> steps,
    ExecutionCostResponse cost
) {

    static AgentExecutionResponse from(AgentExecutionView view) {
        return new AgentExecutionResponse(view.id(), view.flowId(), view.flowVersionId(),
            view.status(), view.result(), view.errorCode(), view.startedAt(), view.finishedAt(),
            view.steps().stream().map(ExecutionStepResponse::from).toList(),
            ExecutionCostResponse.from(view.cost()));
    }
}
