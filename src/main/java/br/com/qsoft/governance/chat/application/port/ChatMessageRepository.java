package br.com.qsoft.governance.chat.application.port;

import br.com.qsoft.governance.chat.domain.ChatMessage;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistência de mensagens; toda leitura cruza tenant + dono da conversa. */
public interface ChatMessageRepository {

    ChatMessage save(ChatMessage message);

    /** Últimas {@code limit} mensagens em ordem cronológica (a mais antiga primeiro). */
    List<ChatMessage> findLastMessages(TenantId tenantId, UUID conversationId, UserId owner,
                                       int limit);

    List<ChatMessage> findByConversation(TenantId tenantId, UUID conversationId, UserId owner);

    default List<ChatMessage> findPage(TenantId tenantId, UUID conversationId, UserId owner,
                                       int page, int size) {
        return findByConversation(tenantId, conversationId, owner);
    }

    /** Primeira mensagem USER com o clientMessageId na conversa do dono. */
    default Optional<ChatMessage> findUserByClientMessageId(TenantId tenantId,
                                                            UUID conversationId, UserId owner,
                                                            String clientMessageId) {
        return Optional.empty();
    }

    /** Primeira mensagem USER do dono com o clientMessageId (primeiro turno). */
    default Optional<ChatMessage> findUserByOwnerAndClientMessageId(TenantId tenantId, UserId owner,
                                                                    String clientMessageId) {
        return Optional.empty();
    }

    /** ASSISTANT seguinte a uma mensagem USER (pelo created_at). */
    default Optional<ChatMessage> findAssistantAfter(TenantId tenantId, UUID conversationId,
                                                     UserId owner, Instant after) {
        return Optional.empty();
    }
}
