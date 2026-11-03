package br.com.qsoft.governance.chat.infrastructure.persistence;

import br.com.qsoft.governance.chat.domain.ConversationStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ConversationJpaRepository extends JpaRepository<ConversationJpaEntity, UUID>,
    JpaSpecificationExecutor<ConversationJpaEntity> {

    /** Leituras por id nunca enxergam conversas DELETED (ADR-42). */
    Optional<ConversationJpaEntity> findByTenantIdAndIdAndOwnerUserIdAndStatusNot(
        UUID tenantId, UUID id, UUID ownerUserId, ConversationStatus excludedStatus);
}
