package br.com.qsoft.governance.chat.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationJpaRepository extends JpaRepository<ConversationJpaEntity, UUID> {

    Optional<ConversationJpaEntity> findByTenantIdAndIdAndOwnerUserId(UUID tenantId, UUID id,
                                                                     UUID ownerUserId);

    Page<ConversationJpaEntity> findByTenantIdAndOwnerUserId(UUID tenantId, UUID ownerUserId,
                                                             Pageable pageable);
}
