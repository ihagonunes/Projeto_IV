package br.com.qsoft.governance.iam.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleAssignmentJpaRepository extends JpaRepository<RoleAssignmentJpaEntity, UUID> {

    List<RoleAssignmentJpaEntity> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    List<RoleAssignmentJpaEntity> findByTenantIdAndUserIdAndRevokedAtIsNull(
        UUID tenantId, UUID userId);

    long countByTenantIdAndRoleIdAndRevokedAtIsNull(UUID tenantId, UUID roleId);

    /**
     * Usuários com ao menos uma concessão ativa e todas elas {@code TENANT_ADMIN}.
     * Consulta nativa tenant-aware (ADR-40 §3).
     */
    @Query(value = """
        SELECT ra.user_id
          FROM iam_role_assignments ra
          JOIN iam_roles r ON r.tenant_id = ra.tenant_id AND r.id = ra.role_id
         WHERE ra.tenant_id = :tenantId
           AND ra.revoked_at IS NULL
         GROUP BY ra.user_id
        HAVING bool_and(r.name = 'TENANT_ADMIN')
        """, nativeQuery = true)
    List<UUID> findUserIdsWithOnlyTenantAdminGrant(@Param("tenantId") UUID tenantId);
}
