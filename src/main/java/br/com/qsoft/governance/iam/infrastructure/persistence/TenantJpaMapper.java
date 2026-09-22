package br.com.qsoft.governance.iam.infrastructure.persistence;

import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.sharedkernel.id.TenantId;

/**
 * Converte entre o agregado de domínio e a entidade JPA.
 */
public final class TenantJpaMapper {

    public TenantJpaEntity toEntity(Tenant tenant) {
        TenantJpaEntity entity = new TenantJpaEntity();
        entity.setId(tenant.id().value());
        entity.setSlug(tenant.slug());
        entity.setName(tenant.name());
        entity.setEmailDomain(tenant.emailDomain());
        entity.setStatus(tenant.status());
        entity.setCreatedAt(tenant.createdAt());
        entity.setUpdatedAt(tenant.updatedAt());
        return entity;
    }

    public Tenant toDomain(TenantJpaEntity entity) {
        return Tenant.restore(
            TenantId.of(entity.getId()),
            entity.getSlug(),
            entity.getName(),
            entity.getEmailDomain(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
