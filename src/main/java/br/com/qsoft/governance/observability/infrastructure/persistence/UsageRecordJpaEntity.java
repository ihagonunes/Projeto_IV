package br.com.qsoft.governance.observability.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * Persistência JPA do UsageRecord. Referências cross-module por ID, sem FK
 * (ADR-29). O {@code @Filter} é defesa secundária de isolamento (ADR-03); a
 * primária é o predicado explícito por tenant_id.
 */
@FilterDef(name = UsageRecordJpaEntity.TENANT_FILTER_NAME,
    parameters = @ParamDef(name = "tenantId", type = UUID.class))
@Filter(name = UsageRecordJpaEntity.TENANT_FILTER_NAME, condition = "tenant_id = :tenantId")
@Entity
@Table(name = "observability_usage_records")
public class UsageRecordJpaEntity {

    public static final String TENANT_FILTER_NAME = "usageRecordTenantFilter";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "request_id", nullable = false, unique = true)
    private UUID requestId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "model_id")
    private UUID modelId;

    @Column(name = "provider_id")
    private UUID providerId;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "estimated_cost", nullable = false, precision = 15, scale = 6)
    private BigDecimal estimatedCost;

    @Column(name = "latency_ms")
    private long latencyMs;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UsageRecordJpaEntity() {
        // para JPA
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getModelId() {
        return modelId;
    }

    public void setModelId(UUID modelId) {
        this.modelId = modelId;
    }

    public UUID getProviderId() {
        return providerId;
    }

    public void setProviderId(UUID providerId) {
        this.providerId = providerId;
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(long inputTokens) {
        this.inputTokens = inputTokens;
    }

    public long getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(long outputTokens) {
        this.outputTokens = outputTokens;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(BigDecimal estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
