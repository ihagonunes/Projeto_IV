package br.com.qsoft.governance.aigateway.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderJpaRepository extends JpaRepository<ProviderJpaEntity, UUID> {

    Optional<ProviderJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<ProviderJpaEntity> findByTenantId(UUID tenantId);

    boolean existsByTenantIdAndName(UUID tenantId, String name);
}
