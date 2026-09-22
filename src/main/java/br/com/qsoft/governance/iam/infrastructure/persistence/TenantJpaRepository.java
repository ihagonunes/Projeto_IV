package br.com.qsoft.governance.iam.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantJpaRepository extends JpaRepository<TenantJpaEntity, UUID> {

    boolean existsBySlug(String slug);

    Optional<TenantJpaEntity> findBySlug(String slug);

    boolean existsByEmailDomain(String emailDomain);

    Optional<TenantJpaEntity> findByEmailDomain(String emailDomain);
}
