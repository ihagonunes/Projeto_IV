package br.com.qsoft.governance.aigateway.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelJpaRepository extends JpaRepository<ModelJpaEntity, UUID> {

    Optional<ModelJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<ModelJpaEntity> findByTenantId(UUID tenantId);

    List<ModelJpaEntity> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    boolean existsByTenantIdAndName(UUID tenantId, String name);
}
