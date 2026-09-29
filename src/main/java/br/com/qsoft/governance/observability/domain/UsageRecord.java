package br.com.qsoft.governance.observability.domain;

import br.com.qsoft.governance.sharedkernel.event.AIRequestCompleted;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * UsageRecord operacional (ADR-13): registro <b>imutável</b> e <b>idempotente
 * por requestId</b> de uma requisição de IA concluída.
 * <p>
 * Não carrega conteúdo privado (prompt, resposta, documento, segredo) — apenas
 * metadados, tokens, custo estimado e latência. O custo vem pronto do Gateway
 * (calculado uma única vez); o observability <b>não recalcula</b>.
 */
public final class UsageRecord {

    private final UUID id;
    private final UUID requestId;
    private final TenantId tenantId;
    private final UUID departmentId;
    private final UUID userId;
    private final UUID modelId;
    private final UUID providerId;
    private final long inputTokens;
    private final long outputTokens;
    private final BigDecimal estimatedCost;
    private final long latencyMs;
    private final String status;
    private final Instant createdAt;

    private UsageRecord(UUID id, UUID requestId, TenantId tenantId, UUID departmentId, UUID userId,
                        UUID modelId, UUID providerId, long inputTokens, long outputTokens,
                        BigDecimal estimatedCost, long latencyMs, String status, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.departmentId = departmentId;
        this.userId = userId;
        this.modelId = modelId;
        this.providerId = providerId;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.estimatedCost = Objects.requireNonNull(estimatedCost, "estimatedCost");
        this.latencyMs = latencyMs;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Cria o registro a partir do evento operacional do Gateway (ADR-13). */
    public static UsageRecord fromEvent(UUID id, AIRequestCompleted event) {
        return new UsageRecord(
            id,
            event.requestId(),
            TenantId.of(event.tenantId()),
            event.departmentId(),
            event.userId(),
            event.modelId(),
            event.providerId(),
            event.inputTokens(),
            event.outputTokens(),
            event.estimatedCost(),
            event.latencyMs(),
            event.status(),
            Instant.now()
        );
    }

    public static UsageRecord restore(UUID id, UUID requestId, TenantId tenantId, UUID departmentId,
                                      UUID userId, UUID modelId, UUID providerId, long inputTokens,
                                      long outputTokens, BigDecimal estimatedCost, long latencyMs,
                                      String status, Instant createdAt) {
        return new UsageRecord(id, requestId, tenantId, departmentId, userId, modelId, providerId,
            inputTokens, outputTokens, estimatedCost, latencyMs, status, createdAt);
    }

    public UUID id() {
        return id;
    }

    public UUID requestId() {
        return requestId;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public UUID departmentId() {
        return departmentId;
    }

    public UUID userId() {
        return userId;
    }

    public UUID modelId() {
        return modelId;
    }

    public UUID providerId() {
        return providerId;
    }

    public long inputTokens() {
        return inputTokens;
    }

    public long outputTokens() {
        return outputTokens;
    }

    public BigDecimal estimatedCost() {
        return estimatedCost;
    }

    public long latencyMs() {
        return latencyMs;
    }

    public String status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
