package br.com.qsoft.governance.agents.domain;

import java.time.Instant;
import java.util.UUID;

/** Resultado de um nó da execução (sem prompt ou conteúdo de erro). */
public record FlowStepResult(String nodeId, String nodeKind, ExecutionStepStatus status,
                             UUID requestId, String errorCode, Instant startedAt,
                             Instant finishedAt) {

    public FlowStepResult {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId é obrigatório.");
        }
        if (status == null) {
            throw new IllegalArgumentException("status é obrigatório.");
        }
    }
}
