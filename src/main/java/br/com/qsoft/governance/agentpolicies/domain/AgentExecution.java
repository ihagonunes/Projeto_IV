package br.com.qsoft.governance.agentpolicies.domain;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Execução do agente (ADR-25): guarda apenas metadados de auditoria —
 * {@code request_id}, documentos citados e ferramentas usadas. Nunca a pergunta
 * ou a resposta.
 */
public final class AgentExecution {

    private final UUID id;
    private final TenantId tenantId;
    private final UserId userId;
    private UUID requestId;
    private AgentExecutionStatus status;
    private List<UUID> documentIds;
    private List<String> toolsCalled;
    private final Instant createdAt;
    private Instant finishedAt;

    private AgentExecution(UUID id, TenantId tenantId, UserId userId, UUID requestId,
                           AgentExecutionStatus status, List<UUID> documentIds,
                           List<String> toolsCalled, Instant createdAt, Instant finishedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.requestId = requestId;
        this.status = Objects.requireNonNull(status, "status");
        this.documentIds = List.copyOf(documentIds);
        this.toolsCalled = List.copyOf(toolsCalled);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.finishedAt = finishedAt;
    }

    public static AgentExecution start(UUID id, TenantId tenantId, UserId userId) {
        return new AgentExecution(id, tenantId, userId, null, AgentExecutionStatus.RUNNING,
            List.of(), List.of(), Instant.now(), null);
    }

    public AgentExecution complete(UUID requestId, Collection<UUID> documentIds,
                                   Collection<String> toolsCalled) {
        this.requestId = requestId;
        this.status = AgentExecutionStatus.COMPLETED;
        this.documentIds = List.copyOf(documentIds);
        this.toolsCalled = List.copyOf(toolsCalled);
        this.finishedAt = Instant.now();
        return this;
    }

    public AgentExecution fail(Collection<String> toolsCalled) {
        this.status = AgentExecutionStatus.FAILED;
        this.toolsCalled = List.copyOf(toolsCalled);
        this.finishedAt = Instant.now();
        return this;
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public UserId userId() {
        return userId;
    }

    public UUID requestId() {
        return requestId;
    }

    public AgentExecutionStatus status() {
        return status;
    }

    public List<UUID> documentIds() {
        return documentIds;
    }

    public List<String> toolsCalled() {
        return toolsCalled;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }
}
