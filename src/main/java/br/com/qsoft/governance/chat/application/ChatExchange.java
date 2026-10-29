package br.com.qsoft.governance.chat.application;

import br.com.qsoft.governance.aigateway.api.completion.AICompletionService;
import br.com.qsoft.governance.aigateway.api.completion.CompletionCommand;
import br.com.qsoft.governance.aigateway.api.completion.CompletionMessage;
import br.com.qsoft.governance.aigateway.api.completion.CompletionResult;
import br.com.qsoft.governance.chat.application.port.ChatMessageRepository;
import br.com.qsoft.governance.chat.domain.ChatMessage;
import br.com.qsoft.governance.chat.domain.Conversation;
import br.com.qsoft.governance.chat.domain.ConversationArchivedException;
import br.com.qsoft.governance.chat.domain.ConversationNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Executa um turno de chat: grava a mensagem USER, chama o
 * {@link AICompletionService} <b>fora</b> de transação e grava a resposta.
 * Em falha do provider, a mensagem USER permanece e nenhuma ASSISTANT é criada.
 */
@Service
public class ChatExchange {

    private static final int HISTORY_LIMIT = 20;

    private final ChatMessageWriter writer;
    private final ChatMessageRepository messageRepository;
    private final AICompletionService completionService;

    public ChatExchange(ChatMessageWriter writer, ChatMessageRepository messageRepository,
                        AICompletionService completionService) {
        this.writer = writer;
        this.messageRepository = messageRepository;
        this.completionService = completionService;
    }

    public ChatTurn exchange(Conversation conversation, String content, UUID requestedModelId) {
        if (conversation.isDeleted()) {
            throw new ConversationNotFoundException();
        }
        if (conversation.isArchived()) {
            throw new ConversationArchivedException();
        }
        ChatMessage userMessage = ChatMessage.user(UUID.randomUUID(), conversation.tenantId(),
            conversation.id(), content);
        writer.persistUserMessage(conversation, userMessage);

        List<ChatMessage> history = messageRepository.findLastMessages(
            conversation.tenantId(), conversation.id(), conversation.ownerUserId(), HISTORY_LIMIT);
        List<CompletionMessage> messages = history.stream()
            .map(message -> new CompletionMessage(message.role().name(), message.content(), null))
            .toList();

        CompletionResult completion = completionService.complete(new CompletionCommand(
            conversation.tenantId(), conversation.ownerUserId(), requestedModelId,
            "CHAT", messages, List.of()));

        ChatMessage assistantMessage = ChatMessage.assistant(UUID.randomUUID(),
            conversation.tenantId(), conversation.id(), completion.content(),
            completion.requestId());
        writer.persistAssistantMessage(conversation, assistantMessage);

        return new ChatTurn(conversation, userMessage, assistantMessage, completion);
    }
}
