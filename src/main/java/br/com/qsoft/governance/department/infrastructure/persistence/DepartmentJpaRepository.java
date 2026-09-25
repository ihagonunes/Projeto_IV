package br.com.qsoft.governance.department.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentJpaRepository extends JpaRepository<DepartmentJpaEntity, UUID> {

    Optional<DepartmentJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<DepartmentJpaEntity> findByTenantId(UUID tenantId);

    boolean existsByTenantIdAndName(UUID tenantId, String name);

    boolean existsByTenantIdAndNameAndIdNot(UUID tenantId, String name, UUID id);
}
