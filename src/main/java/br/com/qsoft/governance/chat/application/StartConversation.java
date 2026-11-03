package br.com.qsoft.governance.chat.application;

import br.com.qsoft.governance.chat.application.port.ChatMessageRepository;
import br.com.qsoft.governance.chat.application.port.ConversationRepository;
import br.com.qsoft.governance.chat.domain.ChatMessage;
import br.com.qsoft.governance.chat.domain.Conversation;
import br.com.qsoft.governance.chat.domain.ConversationNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Cria a conversa (título = primeiros 60 caracteres) e envia a primeira mensagem. */
@Service
public class StartConversation {

    private final ChatMessageWriter writer;
    private final ChatExchange exchange;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatTurnReplay replay;

    public StartConversation(ChatMessageWriter writer, ChatExchange exchange,
                             ConversationRepository conversationRepository,
                             ChatMessageRepository messageRepository,
                             ChatTurnReplay replay) {
        this.writer = writer;
        this.exchange = exchange;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.replay = replay;
    }

    public ChatTurn start(StartConversationCommand command) {
        String clientMessageId = command.clientMessageId();
        if (clientMessageId != null) {
            Optional<ChatMessage> existing = messageRepository.findUserByOwnerAndClientMessageId(
                command.tenantId(), command.owner(), clientMessageId);
            if (existing.isPresent()) {
                Conversation conversation = conversationRepository
                    .findByTenantIdAndIdAndOwner(command.tenantId(),
                        existing.get().conversationId(), command.owner())
                    .orElseThrow(ConversationNotFoundException::new);
                return replay.replay(conversation, existing.get());
            }
        }

        Conversation conversation = Conversation.start(UUID.randomUUID(), command.tenantId(),
            command.owner(), command.message());
        writer.createConversation(conversation);
        return exchange.exchange(conversation, command.message(), command.requestedModelId(),
            clientMessageId);
    }
}
