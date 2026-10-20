package br.com.qsoft.governance.chat.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageJpaRepository extends JpaRepository<ChatMessageJpaEntity, UUID> {

    /**
     * Todas as mensagens da conversa do dono, em ordem cronológica. O join com a
     * conversa garante tenant + dono na mesma query (ADR-39).
     */
    @Query(value = """
        SELECT m.* FROM chat_messages m
          JOIN chat_conversations c ON c.id = m.conversation_id
         WHERE m.tenant_id = :tenantId
           AND m.conversation_id = :conversationId
           AND c.owner_user_id = :ownerUserId
         ORDER BY m.created_at ASC
        """, nativeQuery = true)
    List<ChatMessageJpaEntity> findByOwner(@Param("tenantId") UUID tenantId,
                                           @Param("conversationId") UUID conversationId,
                                           @Param("ownerUserId") UUID ownerUserId);

    /** Últimas N mensagens (a mais recente primeiro); o adapter inverte. */
    @Query(value = """
        SELECT m.* FROM chat_messages m
          JOIN chat_conversations c ON c.id = m.conversation_id
         WHERE m.tenant_id = :tenantId
           AND m.conversation_id = :conversationId
           AND c.owner_user_id = :ownerUserId
         ORDER BY m.created_at DESC
         LIMIT :limit
        """, nativeQuery = true)
    List<ChatMessageJpaEntity> findLastByOwner(@Param("tenantId") UUID tenantId,
                                               @Param("conversationId") UUID conversationId,
                                               @Param("ownerUserId") UUID ownerUserId,
                                               @Param("limit") int limit);
}
