package br.com.qsoft.governance.agents.infrastructure.web;

import br.com.qsoft.governance.agents.application.AgentExecutionView;
import java.math.BigDecimal;

/** Custo agregado; `INCONCLUSIVE` nunca vira zero. */
public record ExecutionCostResponse(String status, BigDecimal estimatedCost) {

    static ExecutionCostResponse from(AgentExecutionView.ExecutionCost cost) {
        return new ExecutionCostResponse(cost.status(), cost.estimatedCost());
    }
}
