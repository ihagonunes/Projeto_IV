package br.com.qsoft.governance.agents.infrastructure.web;

import br.com.qsoft.governance.agents.application.AgentExecutionView;
import br.com.qsoft.governance.agents.domain.ExecutionStepStatus;
import java.time.Instant;
import java.util.UUID;

/** Etapa da execução com status e recibo. */
public record ExecutionStepResponse(
    String nodeId,
    String nodeKind,
    ExecutionStepStatus status,
    UUID requestId,
    String errorCode,
    Instant startedAt,
    Instant finishedAt,
    ExecutionReceiptResponse receipt
) {

    static ExecutionStepResponse from(AgentExecutionView.StepView step) {
        return new ExecutionStepResponse(step.nodeId(), step.nodeKind(), step.status(),
            step.requestId(), step.errorCode(), step.startedAt(), step.finishedAt(),
            ExecutionReceiptResponse.from(step.receipt()));
    }
}
