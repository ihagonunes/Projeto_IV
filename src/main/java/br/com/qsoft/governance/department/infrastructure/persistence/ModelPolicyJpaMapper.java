package br.com.qsoft.governance.department.infrastructure.persistence;

import br.com.qsoft.governance.department.domain.modelpolicy.ModelPolicy;

/** Converte entre a ModelPolicy e a entidade JPA. */
public final class ModelPolicyJpaMapper {

    public ModelPolicyJpaEntity toEntity(ModelPolicy policy, java.util.UUID tenantId) {
        ModelPolicyJpaEntity entity = new ModelPolicyJpaEntity();
        entity.setDepartmentId(policy.departmentId());
        entity.setTenantId(tenantId);
        entity.setDefaultModelId(policy.defaultModelId());
        entity.setAllowedModelIds(policy.allowedModelIds());
        entity.setCreatedAt(policy.createdAt());
        entity.setUpdatedAt(policy.updatedAt());
        return entity;
    }

    public ModelPolicy toDomain(ModelPolicyJpaEntity entity) {
        return ModelPolicy.restore(
            entity.getDepartmentId(),
            entity.getAllowedModelIds(),
            entity.getDefaultModelId(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
