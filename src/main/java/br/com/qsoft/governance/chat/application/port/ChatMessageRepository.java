package br.com.qsoft.governance.chat.application.port;

import br.com.qsoft.governance.chat.domain.ChatMessage;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.List;
import java.util.UUID;

/** Persistência de mensagens; toda leitura cruza tenant + dono da conversa. */
public interface ChatMessageRepository {

    ChatMessage save(ChatMessage message);

    /** Últimas {@code limit} mensagens em ordem cronológica (a mais antiga primeiro). */
    List<ChatMessage> findLastMessages(TenantId tenantId, UUID conversationId, UserId owner,
                                       int limit);

    List<ChatMessage> findByConversation(TenantId tenantId, UUID conversationId, UserId owner);
}
