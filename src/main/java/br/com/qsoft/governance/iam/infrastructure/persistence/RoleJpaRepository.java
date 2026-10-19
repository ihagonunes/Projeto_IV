package br.com.qsoft.governance.iam.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleJpaRepository extends JpaRepository<RoleJpaEntity, UUID> {

    Optional<RoleJpaEntity> findByTenantIdAndName(UUID tenantId, String name);

    /** Lock pessimista na linha do papel — serializa a guarda do último admin. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RoleJpaEntity r where r.tenantId = :tenantId and r.name = :name")
    Optional<RoleJpaEntity> findByTenantIdAndNameForUpdate(
        @Param("tenantId") UUID tenantId, @Param("name") String name);

    boolean existsByTenantIdAndName(UUID tenantId, String name);
}
