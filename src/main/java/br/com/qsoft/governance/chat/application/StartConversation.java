package br.com.qsoft.governance.chat.application;

import br.com.qsoft.governance.chat.domain.Conversation;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Cria a conversa (título = primeiros 60 caracteres) e envia a primeira mensagem. */
@Service
public class StartConversation {

    private final ChatMessageWriter writer;
    private final ChatExchange exchange;

    public StartConversation(ChatMessageWriter writer, ChatExchange exchange) {
        this.writer = writer;
        this.exchange = exchange;
    }

    public ChatTurn start(StartConversationCommand command) {
        Conversation conversation = Conversation.start(UUID.randomUUID(), command.tenantId(),
            command.owner(), command.message());
        writer.createConversation(conversation);
        return exchange.exchange(conversation, command.message(), command.requestedModelId());
    }
}
