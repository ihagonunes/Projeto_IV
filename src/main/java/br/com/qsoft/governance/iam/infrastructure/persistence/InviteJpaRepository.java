package br.com.qsoft.governance.iam.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InviteJpaRepository extends JpaRepository<InviteJpaEntity, UUID> {

    Optional<InviteJpaEntity> findByTokenHash(String tokenHash);

    boolean existsByTokenHash(String tokenHash);

    List<InviteJpaEntity> findByTenantId(UUID tenantId);
}
