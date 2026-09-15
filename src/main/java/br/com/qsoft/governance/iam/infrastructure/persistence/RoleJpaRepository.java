package br.com.qsoft.governance.iam.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleJpaRepository extends JpaRepository<RoleJpaEntity, UUID> {

    Optional<RoleJpaEntity> findByTenantIdAndName(UUID tenantId, String name);

    boolean existsByTenantIdAndName(UUID tenantId, String name);
}
