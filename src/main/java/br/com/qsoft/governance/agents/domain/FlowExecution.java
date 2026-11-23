package br.com.qsoft.governance.agents.domain;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * Execução de uma versão publicada (ADR-45). Não guarda prompt nem entrada.
 * O {@code result} é <b>transiente</b> (decisão 4-B): existe apenas na resposta
 * síncrona do POST e nunca é persistido; GET/replay não devolvem conteúdo.
 */
public record FlowExecution(
    UUID id,
    TenantId tenantId,
    UUID flowId,
    UUID flowVersionId,
    UserId ownerUserId,
    ExecutionStatus status,
    String idempotencyKey,
    String result,
    String errorCode,
    Instant startedAt,
    Instant finishedAt,
    Instant createdAt
) {

    public FlowExecution {
        if (id == null) {
            throw new IllegalArgumentException("id é obrigatório.");
        }
        if (tenantId == null || ownerUserId == null) {
            throw new IllegalArgumentException("tenant/owner são obrigatórios.");
        }
        if (flowId == null || flowVersionId == null) {
            throw new IllegalArgumentException("flow/version são obrigatórios.");
        }
        if (status == null) {
            throw new IllegalArgumentException("status é obrigatório.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey é obrigatória.");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt é obrigatório.");
        }
    }
}
