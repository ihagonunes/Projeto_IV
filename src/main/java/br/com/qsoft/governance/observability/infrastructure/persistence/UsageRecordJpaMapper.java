package br.com.qsoft.governance.observability.infrastructure.persistence;

import br.com.qsoft.governance.observability.domain.UsageRecord;
import br.com.qsoft.governance.sharedkernel.id.TenantId;

/** Converte entre o UsageRecord e a entidade JPA. */
public final class UsageRecordJpaMapper {

    public UsageRecordJpaEntity toEntity(UsageRecord record) {
        UsageRecordJpaEntity entity = new UsageRecordJpaEntity();
        entity.setId(record.id());
        entity.setRequestId(record.requestId());
        entity.setTenantId(record.tenantId().value());
        entity.setDepartmentId(record.departmentId());
        entity.setUserId(record.userId());
        entity.setModelId(record.modelId());
        entity.setProviderId(record.providerId());
        entity.setInputTokens(record.inputTokens());
        entity.setOutputTokens(record.outputTokens());
        entity.setEstimatedCost(record.estimatedCost());
        entity.setLatencyMs(record.latencyMs());
        entity.setStatus(record.status());
        entity.setCreatedAt(record.createdAt());
        return entity;
    }

    public UsageRecord toDomain(UsageRecordJpaEntity entity) {
        return UsageRecord.restore(
            entity.getId(),
            entity.getRequestId(),
            TenantId.of(entity.getTenantId()),
            entity.getDepartmentId(),
            entity.getUserId(),
            entity.getModelId(),
            entity.getProviderId(),
            entity.getInputTokens(),
            entity.getOutputTokens(),
            entity.getEstimatedCost(),
            entity.getLatencyMs(),
            entity.getStatus(),
            entity.getCreatedAt()
        );
    }
}
