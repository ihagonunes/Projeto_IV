package br.com.qsoft.governance.chat.application;

import br.com.qsoft.governance.chat.application.port.ChatMessageRepository;
import br.com.qsoft.governance.chat.application.port.ConversationRepository;
import br.com.qsoft.governance.chat.domain.ChatMessage;
import br.com.qsoft.governance.chat.domain.Conversation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistência transacional curta do chat (ADR-39). Cada chamada abre e fecha
 * sua própria transação, para que o envio não segure transação durante a
 * chamada ao provider.
 */
@Service
public class ChatMessageWriter {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;

    public ChatMessageWriter(ConversationRepository conversationRepository,
                             ChatMessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public void createConversation(Conversation conversation) {
        conversationRepository.save(conversation);
    }

    /** Transação 1 do envio: grava a mensagem USER e atualiza a conversa. */
    @Transactional
    public void persistUserMessage(Conversation conversation, ChatMessage message) {
        conversationRepository.save(conversation.touch());
        messageRepository.save(message);
    }

    /** Transação 2 do envio: grava a mensagem ASSISTANT e atualiza a conversa. */
    @Transactional
    public void persistAssistantMessage(Conversation conversation, ChatMessage message) {
        conversationRepository.save(conversation.touch());
        messageRepository.save(message);
    }
}
