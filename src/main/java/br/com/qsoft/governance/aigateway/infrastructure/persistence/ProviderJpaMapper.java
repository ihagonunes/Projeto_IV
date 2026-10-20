package br.com.qsoft.governance.aigateway.infrastructure.persistence;

import br.com.qsoft.governance.aigateway.domain.provider.Provider;
import br.com.qsoft.governance.sharedkernel.id.TenantId;

/** Converte entre o agregado Provider e a entidade JPA. */
public final class ProviderJpaMapper {

    public ProviderJpaEntity toEntity(Provider provider) {
        ProviderJpaEntity entity = new ProviderJpaEntity();
        entity.setId(provider.id());
        entity.setTenantId(provider.tenantId().value());
        entity.setName(provider.name());
        entity.setStatus(provider.status());
        entity.setKind(provider.kind());
        entity.setBaseUrl(provider.baseUrl());
        entity.setCredentialRef(provider.credentialRef());
        entity.setTimeoutMs(provider.timeoutMs());
        entity.setCreatedAt(provider.createdAt());
        entity.setUpdatedAt(provider.updatedAt());
        return entity;
    }

    public Provider toDomain(ProviderJpaEntity entity) {
        return Provider.restore(
            entity.getId(),
            TenantId.of(entity.getTenantId()),
            entity.getName(),
            entity.getKind(),
            entity.getBaseUrl(),
            entity.getCredentialRef(),
            entity.getTimeoutMs(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
