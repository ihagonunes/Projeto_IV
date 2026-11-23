package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.aigateway.api.model.ReceiptInfo;
import br.com.qsoft.governance.agents.domain.ExecutionStatus;
import br.com.qsoft.governance.agents.domain.ExecutionStepStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** View da execução com etapas e recibos; custo agregado preserva INCONCLUSIVE. */
public record AgentExecutionView(
    UUID id,
    UUID flowId,
    UUID flowVersionId,
    ExecutionStatus status,
    String result,
    String errorCode,
    Instant startedAt,
    Instant finishedAt,
    List<StepView> steps,
    ExecutionCost cost
) {

    public AgentExecutionView {
        steps = steps == null ? List.of() : List.copyOf(steps);
        Objects.requireNonNull(cost, "cost é obrigatório.");
    }

    public record StepView(
        String nodeId,
        String nodeKind,
        ExecutionStepStatus status,
        UUID requestId,
        String errorCode,
        Instant startedAt,
        Instant finishedAt,
        ReceiptInfo receipt
    ) {
    }

    public record ExecutionCost(String status, BigDecimal estimatedCost) {
    }

    static ExecutionCost aggregateCost(List<StepView> steps) {
        List<StepView> withRequest = steps.stream()
            .filter(step -> step.requestId() != null)
            .toList();
        if (withRequest.isEmpty()) {
            return new ExecutionCost("NOT_APPLICABLE", null);
        }
        boolean missing = withRequest.stream().anyMatch(step -> step.receipt() == null);
        boolean inconclusive = withRequest.stream()
            .map(StepView::receipt)
            .filter(Objects::nonNull)
            .anyMatch(receipt -> "INCONCLUSIVE".equals(receipt.costStatus()));
        if (missing || inconclusive) {
            return new ExecutionCost("INCONCLUSIVE", null);
        }
        List<ReceiptInfo> known = withRequest.stream().map(StepView::receipt)
            .filter(receipt -> "KNOWN".equals(receipt.costStatus()))
            .toList();
        if (known.isEmpty()) {
            return new ExecutionCost("NOT_APPLICABLE", null);
        }
        BigDecimal total = known.stream().map(ReceiptInfo::estimatedCost)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ExecutionCost("KNOWN", total);
    }
}
