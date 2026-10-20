package br.com.qsoft.governance.aigateway.infrastructure.persistence;

import br.com.qsoft.governance.aigateway.domain.attempt.AuthorizationStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.CostStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.DeliveryStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.ExecutionStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.RequestPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistência JPA da tentativa durável. Sem FK cross-module: {@code tenant_id},
 * {@code user_id} e {@code department_id} são referências por ID. A tabela
 * <b>não</b> guarda conteúdo, prompt, resposta ou segredo (TR11).
 */
@Entity
@Table(name = "aigateway_request_attempts")
public class RequestAttemptJpaEntity {

    @Id
    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private RequestPurpose purpose;

    @Column(name = "model_id")
    private UUID modelId;

    @Column(name = "provider_id")
    private UUID providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "authorization_status", nullable = false, length = 16)
    private AuthorizationStatus authorizationStatus;

    @Column(name = "denial_reason", length = 30)
    private String denialReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false, length = 20)
    private ExecutionStatus executionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private DeliveryStatus deliveryStatus;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_status", nullable = false, length = 20)
    private CostStatus costStatus;

    @Column(name = "estimated_cost", precision = 15, scale = 6)
    private BigDecimal estimatedCost;

    @Column(name = "unit_price_input", precision = 12, scale = 6)
    private BigDecimal unitPriceInput;

    @Column(name = "unit_price_output", precision = 12, scale = 6)
    private BigDecimal unitPriceOutput;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "delivery_attempts", nullable = false)
    private short deliveryAttempts;

    @Column(name = "next_delivery_at")
    private Instant nextDeliveryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RequestAttemptJpaEntity() {
        // para JPA
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

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public RequestPurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(RequestPurpose purpose) {
        this.purpose = purpose;
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

    public AuthorizationStatus getAuthorizationStatus() {
        return authorizationStatus;
    }

    public void setAuthorizationStatus(AuthorizationStatus authorizationStatus) {
        this.authorizationStatus = authorizationStatus;
    }

    public String getDenialReason() {
        return denialReason;
    }

    public void setDenialReason(String denialReason) {
        this.denialReason = denialReason;
    }

    public ExecutionStatus getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(ExecutionStatus executionStatus) {
        this.executionStatus = executionStatus;
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(DeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public Long getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(Long inputTokens) {
        this.inputTokens = inputTokens;
    }

    public Long getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Long outputTokens) {
        this.outputTokens = outputTokens;
    }

    public CostStatus getCostStatus() {
        return costStatus;
    }

    public void setCostStatus(CostStatus costStatus) {
        this.costStatus = costStatus;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(BigDecimal estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    public BigDecimal getUnitPriceInput() {
        return unitPriceInput;
    }

    public void setUnitPriceInput(BigDecimal unitPriceInput) {
        this.unitPriceInput = unitPriceInput;
    }

    public BigDecimal getUnitPriceOutput() {
        return unitPriceOutput;
    }

    public void setUnitPriceOutput(BigDecimal unitPriceOutput) {
        this.unitPriceOutput = unitPriceOutput;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public short getDeliveryAttempts() {
        return deliveryAttempts;
    }

    public void setDeliveryAttempts(short deliveryAttempts) {
        this.deliveryAttempts = deliveryAttempts;
    }

    public Instant getNextDeliveryAt() {
        return nextDeliveryAt;
    }

    public void setNextDeliveryAt(Instant nextDeliveryAt) {
        this.nextDeliveryAt = nextDeliveryAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
