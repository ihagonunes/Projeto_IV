package br.com.qsoft.governance.aigateway.infrastructure.persistence;

import br.com.qsoft.governance.aigateway.domain.attempt.RequestAttempt;

/** Converte entre a tentativa de domínio e a entidade JPA. */
public final class RequestAttemptJpaMapper {

    public RequestAttemptJpaEntity toEntity(RequestAttempt attempt) {
        RequestAttemptJpaEntity entity = new RequestAttemptJpaEntity();
        entity.setRequestId(attempt.requestId());
        entity.setTenantId(attempt.tenantId());
        entity.setUserId(attempt.userId());
        entity.setDepartmentId(attempt.departmentId());
        entity.setPurpose(attempt.purpose());
        entity.setModelId(attempt.modelId());
        entity.setProviderId(attempt.providerId());
        entity.setAuthorizationStatus(attempt.authorizationStatus());
        entity.setDenialReason(attempt.denialReason());
        entity.setExecutionStatus(attempt.executionStatus());
        entity.setDeliveryStatus(attempt.deliveryStatus());
        entity.setInputTokens(attempt.inputTokens());
        entity.setOutputTokens(attempt.outputTokens());
        entity.setCostStatus(attempt.costStatus());
        entity.setEstimatedCost(attempt.estimatedCost());
        entity.setUnitPriceInput(attempt.unitPriceInput());
        entity.setUnitPriceOutput(attempt.unitPriceOutput());
        entity.setLatencyMs(attempt.latencyMs());
        entity.setErrorCode(attempt.errorCode());
        entity.setDeliveryAttempts((short) attempt.deliveryAttempts());
        entity.setNextDeliveryAt(attempt.nextDeliveryAt());
        entity.setCreatedAt(attempt.createdAt());
        entity.setUpdatedAt(attempt.updatedAt());
        return entity;
    }

    public RequestAttempt toDomain(RequestAttemptJpaEntity entity) {
        return new RequestAttempt(
            entity.getRequestId(),
            entity.getTenantId(),
            entity.getUserId(),
            entity.getDepartmentId(),
            entity.getPurpose(),
            entity.getModelId(),
            entity.getProviderId(),
            entity.getAuthorizationStatus(),
            entity.getDenialReason(),
            entity.getExecutionStatus(),
            entity.getDeliveryStatus(),
            entity.getInputTokens(),
            entity.getOutputTokens(),
            entity.getCostStatus(),
            entity.getEstimatedCost(),
            entity.getUnitPriceInput(),
            entity.getUnitPriceOutput(),
            entity.getLatencyMs(),
            entity.getErrorCode(),
            entity.getDeliveryAttempts(),
            entity.getNextDeliveryAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
