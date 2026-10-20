package br.com.qsoft.governance.chat.application;

import br.com.qsoft.governance.chat.application.port.ConversationRepository;
import br.com.qsoft.governance.chat.domain.Conversation;
import br.com.qsoft.governance.chat.domain.ConversationNotFoundException;
import org.springframework.stereotype.Service;

/** Envia uma mensagem em conversa existente do próprio ator. */
@Service
public class SendMessage {

    private final ConversationRepository conversationRepository;
    private final ChatExchange exchange;

    public SendMessage(ConversationRepository conversationRepository, ChatExchange exchange) {
        this.conversationRepository = conversationRepository;
        this.exchange = exchange;
    }

    public ChatTurn send(SendMessageCommand command) {
        Conversation conversation = conversationRepository.findByTenantIdAndIdAndOwner(
                command.tenantId(), command.conversationId(), command.owner())
            .orElseThrow(ConversationNotFoundException::new);
        return exchange.exchange(conversation, command.message(), command.requestedModelId());
    }
}
