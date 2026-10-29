package br.com.qsoft.governance.chat.application;

import br.com.qsoft.governance.chat.application.port.ConversationRepository;
import br.com.qsoft.governance.chat.domain.Conversation;
import br.com.qsoft.governance.chat.domain.ConversationNotFoundException;
import org.springframework.stereotype.Service;

/** Renomeia, arquiva ou restaura uma conversa do próprio ator (ADR-42). */
@Service
public class UpdateConversation {

    private final ConversationRepository conversationRepository;

    public UpdateConversation(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    public Conversation update(UpdateConversationCommand command) {
        Conversation conversation = conversationRepository.findByTenantIdAndIdAndOwner(
                command.tenantId(), command.conversationId(), command.owner())
            .orElseThrow(ConversationNotFoundException::new);

        boolean hasTitle = command.title() != null;
        boolean hasStatus = command.status() != null;
        if (!hasTitle && !hasStatus) {
            throw new IllegalArgumentException("Informe ao menos um campo para atualizar.");
        }
        if (hasTitle) {
            conversation.rename(command.title());
        }
        if (hasStatus) {
            switch (command.status()) {
                case "ARCHIVED" -> conversation.archive();
                case "ACTIVE" -> conversation.restore();
                default -> throw new IllegalArgumentException("status inválido.");
            }
        }
        return conversationRepository.save(conversation);
    }
}
