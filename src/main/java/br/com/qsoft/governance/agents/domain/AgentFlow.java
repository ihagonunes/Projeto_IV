package br.com.qsoft.governance.agents.domain;

import static br.com.qsoft.governance.agents.domain.AgentInvariants.requireNonNull;
import static br.com.qsoft.governance.agents.domain.AgentInvariants.requireText;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * Metadados de um fluxo de agentes (ADR-44). O grafo vive em
 * {@link AgentFlowVersion}; aqui ficam dono, tenant, status, versão corrente e
 * controle de concorrência otimista.
 * <p>
 * {@code tenantId}/{@code ownerUserId} sempre vêm do contexto autenticado, nunca
 * do cliente. {@code deletedAt} é arquivamento lógico.
 */
public record AgentFlow(
    UUID id,
    TenantId tenantId,
    UserId ownerUserId,
    UUID departmentId,
    String name,
    AgentFlowStatus status,
    UUID currentVersionId,
    long rowVersion,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt
) {

    public AgentFlow {
        requireNonNull(id, "id");
        requireNonNull(tenantId, "tenantId");
        requireNonNull(ownerUserId, "ownerUserId");
        name = requireText(name, "name", 120);
        requireNonNull(status, "status");
        if (rowVersion < 1) {
            throw new IllegalArgumentException("rowVersion deve ser maior que zero.");
        }
        requireNonNull(createdAt, "createdAt");
        requireNonNull(updatedAt, "updatedAt");
        if (status == AgentFlowStatus.PUBLISHED && currentVersionId == null) {
            throw new IllegalArgumentException(
                "currentVersionId é obrigatório para fluxo PUBLISHED.");
        }
    }

    /** Novo fluxo em rascunho, sem versão publicada. */
    public static AgentFlow create(UUID id, TenantId tenantId, UserId ownerUserId,
                                   UUID departmentId, String name, Instant now) {
        requireNonNull(now, "now");
        return new AgentFlow(id, tenantId, ownerUserId, departmentId, name,
            AgentFlowStatus.DRAFT, null, 1L, now, now, null);
    }
}
