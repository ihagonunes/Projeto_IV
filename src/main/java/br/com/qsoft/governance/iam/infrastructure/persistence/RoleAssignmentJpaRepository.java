package br.com.qsoft.governance.iam.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleAssignmentJpaRepository extends JpaRepository<RoleAssignmentJpaEntity, UUID> {

    List<RoleAssignmentJpaEntity> findByTenantIdAndUserId(UUID tenantId, UUID userId);
}
