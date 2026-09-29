package br.com.qsoft.governance.aigateway.infrastructure.persistence;

import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.sharedkernel.id.TenantId;

/** Converte entre o agregado Model e a entidade JPA. */
public final class ModelJpaMapper {

    public ModelJpaEntity toEntity(Model model) {
        ModelJpaEntity entity = new ModelJpaEntity();
        entity.setId(model.id());
        entity.setTenantId(model.tenantId().value());
        entity.setProviderId(model.providerId());
        entity.setName(model.name());
        entity.setStatus(model.status());
        entity.setInputPricePer1k(model.inputPricePer1k());
        entity.setOutputPricePer1k(model.outputPricePer1k());
        entity.setCreatedAt(model.createdAt());
        entity.setUpdatedAt(model.updatedAt());
        return entity;
    }

    public Model toDomain(ModelJpaEntity entity) {
        return Model.restore(
            entity.getId(),
            TenantId.of(entity.getTenantId()),
            entity.getProviderId(),
            entity.getName(),
            entity.getStatus(),
            entity.getInputPricePer1k(),
            entity.getOutputPricePer1k(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
