package br.com.qsoft.governance.chat.application;

import br.com.qsoft.governance.chat.application.port.ChatMessageRepository;
import br.com.qsoft.governance.chat.application.port.ConversationRepository;
import br.com.qsoft.governance.chat.domain.ChatMessage;
import br.com.qsoft.governance.chat.domain.Conversation;
import br.com.qsoft.governance.chat.domain.ConversationNotFoundException;
import br.com.qsoft.governance.chat.domain.TurnInProgressOrFailedException;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Envia uma mensagem em conversa existente do próprio ator. */
@Service
public class SendMessage {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatExchange exchange;
    private final ChatTurnReplay replay;

    public SendMessage(ConversationRepository conversationRepository,
                       ChatMessageRepository messageRepository,
                       ChatExchange exchange,
                       ChatTurnReplay replay) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.exchange = exchange;
        this.replay = replay;
    }

    public ChatTurn send(SendMessageCommand command) {
        Conversation conversation = conversationRepository.findByTenantIdAndIdAndOwner(
                command.tenantId(), command.conversationId(), command.owner())
            .orElseThrow(ConversationNotFoundException::new);

        String clientMessageId = command.clientMessageId();
        if (clientMessageId != null) {
            Optional<ChatMessage> existing = messageRepository.findUserByClientMessageId(
                command.tenantId(), command.conversationId(), command.owner(), clientMessageId);
            if (existing.isPresent()) {
                return replay.replay(conversation, existing.get());
            }
        }

        try {
            return exchange.exchange(conversation, command.message(), command.requestedModelId(),
                clientMessageId);
        } catch (DataIntegrityViolationException ex) {
            if (clientMessageId == null) {
                throw ex;
            }
            return messageRepository.findUserByClientMessageId(command.tenantId(),
                    command.conversationId(), command.owner(), clientMessageId)
                .map(existing -> replay.replay(conversation, existing))
                .orElseThrow(() -> new TurnInProgressOrFailedException(null, null));
        }
    }
}
