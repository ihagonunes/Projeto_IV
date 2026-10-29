package br.com.qsoft.governance.chat.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

    @Query(value = """
        SELECT m.* FROM chat_messages m
          JOIN chat_conversations c ON c.id = m.conversation_id
         WHERE m.tenant_id = :tenantId
           AND m.conversation_id = :conversationId
           AND c.owner_user_id = :ownerUserId
           AND m.role = 'USER'
           AND m.client_message_id = :clientMessageId
         ORDER BY m.created_at ASC
         LIMIT 1
        """, nativeQuery = true)
    Optional<ChatMessageJpaEntity> findUserByClientMessageId(
        @Param("tenantId") UUID tenantId, @Param("conversationId") UUID conversationId,
        @Param("ownerUserId") UUID ownerUserId, @Param("clientMessageId") String clientMessageId);

    @Query(value = """
        SELECT m.* FROM chat_messages m
          JOIN chat_conversations c ON c.id = m.conversation_id
         WHERE m.tenant_id = :tenantId
           AND c.owner_user_id = :ownerUserId
           AND m.role = 'USER'
           AND m.client_message_id = :clientMessageId
         ORDER BY m.created_at ASC
         LIMIT 1
        """, nativeQuery = true)
    Optional<ChatMessageJpaEntity> findUserByOwnerAndClientMessageId(
        @Param("tenantId") UUID tenantId, @Param("ownerUserId") UUID ownerUserId,
        @Param("clientMessageId") String clientMessageId);

    @Query(value = """
        SELECT m.* FROM chat_messages m
          JOIN chat_conversations c ON c.id = m.conversation_id
         WHERE m.tenant_id = :tenantId
           AND m.conversation_id = :conversationId
           AND c.owner_user_id = :ownerUserId
           AND m.role = 'ASSISTANT'
           AND m.created_at >= :after
         ORDER BY m.created_at ASC
         LIMIT 1
        """, nativeQuery = true)
    Optional<ChatMessageJpaEntity> findAssistantAfter(
        @Param("tenantId") UUID tenantId, @Param("conversationId") UUID conversationId,
        @Param("ownerUserId") UUID ownerUserId, @Param("after") Instant after);

    @Query(value = """
        SELECT m.* FROM chat_messages m
          JOIN chat_conversations c ON c.id = m.conversation_id
         WHERE m.tenant_id = :tenantId
           AND m.conversation_id = :conversationId
           AND c.owner_user_id = :ownerUserId
         ORDER BY m.created_at ASC
         LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<ChatMessageJpaEntity> findPageByOwner(
        @Param("tenantId") UUID tenantId, @Param("conversationId") UUID conversationId,
        @Param("ownerUserId") UUID ownerUserId, @Param("limit") int limit,
        @Param("offset") int offset);
}
