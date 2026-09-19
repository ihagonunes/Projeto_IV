package br.com.qsoft.governance.iam.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);

    Optional<UserJpaEntity> findByTenantIdAndEmail(UUID tenantId, String email);

    /**
     * Busca por id restringida ao tenant. Usada quando há um TenantContext
     * (o @Filter Hibernate não se aplica ao find() por chave primária).
     */
    Optional<UserJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    java.util.List<UserJpaEntity> findAllByTenantId(UUID tenantId);
}
