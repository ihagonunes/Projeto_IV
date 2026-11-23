package br.com.qsoft.governance.agents.domain;

import java.util.List;

/** Resultado completo do motor topológico; sem prompt/entrada. */
public record FlowExecutionResult(ExecutionStatus status, String result, String errorCode,
                                  List<FlowStepResult> steps) {

    public FlowExecutionResult {
        if (status == null) {
            throw new IllegalArgumentException("status é obrigatório.");
        }
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
