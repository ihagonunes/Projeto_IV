package br.com.qsoft.governance.agents.domain;

import java.time.Instant;
import java.util.UUID;

/** Etapa persistida de uma execução; sem conteúdo de prompt/resposta. */
public record ExecutionStep(
    UUID id,
    UUID executionId,
    int position,
    String nodeId,
    String nodeKind,
    ExecutionStepStatus status,
    UUID requestId,
    String errorCode,
    Instant startedAt,
    Instant finishedAt
) {

    public ExecutionStep {
        if (id == null || executionId == null) {
            throw new IllegalArgumentException("id/executionId são obrigatórios.");
        }
        if (position < 0) {
            throw new IllegalArgumentException("position não pode ser negativa.");
        }
        if (nodeId == null || nodeId.isBlank() || nodeKind == null) {
            throw new IllegalArgumentException("nodeId/nodeKind são obrigatórios.");
        }
        if (status == null) {
            throw new IllegalArgumentException("status é obrigatório.");
        }
    }
}
