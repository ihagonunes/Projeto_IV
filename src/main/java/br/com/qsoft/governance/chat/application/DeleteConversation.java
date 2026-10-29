package br.com.qsoft.governance.chat.application;

import br.com.qsoft.governance.chat.application.port.ConversationRepository;
import br.com.qsoft.governance.chat.domain.Conversation;
import br.com.qsoft.governance.chat.domain.ConversationNotFoundException;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Exclusão lógica de uma conversa do próprio ator (ADR-42). */
@Service
public class DeleteConversation {

    private final ConversationRepository conversationRepository;

    public DeleteConversation(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    public void delete(TenantId tenantId, UserId owner, UUID conversationId) {
        Conversation conversation = conversationRepository.findByTenantIdAndIdAndOwner(
                tenantId, conversationId, owner)
            .orElseThrow(ConversationNotFoundException::new);
        conversationRepository.save(conversation.delete());
    }
}
