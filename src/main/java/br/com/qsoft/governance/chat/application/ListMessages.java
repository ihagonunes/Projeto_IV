package br.com.qsoft.governance.chat.application;

import br.com.qsoft.governance.chat.application.port.ChatMessageRepository;
import br.com.qsoft.governance.chat.application.port.ConversationRepository;
import br.com.qsoft.governance.chat.domain.ChatMessage;
import br.com.qsoft.governance.chat.domain.ConversationNotFoundException;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Mensagens da conversa do próprio ator; conversa de outra pessoa → 404. */
@Service
public class ListMessages {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;

    public ListMessages(ConversationRepository conversationRepository,
                        ChatMessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    public List<ChatMessage> list(TenantId tenantId, UserId owner, UUID conversationId) {
        conversationRepository.findByTenantIdAndIdAndOwner(tenantId, conversationId, owner)
            .orElseThrow(ConversationNotFoundException::new);
        return messageRepository.findByConversation(tenantId, conversationId, owner);
    }
}
