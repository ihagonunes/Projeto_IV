package br.com.qsoft.governance.department.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelPolicyJpaRepository extends JpaRepository<ModelPolicyJpaEntity, UUID> {

    Optional<ModelPolicyJpaEntity> findByTenantIdAndDepartmentId(UUID tenantId, UUID departmentId);
}
