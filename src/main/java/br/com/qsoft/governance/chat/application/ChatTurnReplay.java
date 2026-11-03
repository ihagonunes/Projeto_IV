package br.com.qsoft.governance.chat.application;

import br.com.qsoft.governance.aigateway.api.completion.CompletionResult;
import br.com.qsoft.governance.aigateway.api.model.ReceiptInfo;
import br.com.qsoft.governance.aigateway.api.query.AttemptReceiptQuery;
import br.com.qsoft.governance.chat.application.port.ChatMessageRepository;
import br.com.qsoft.governance.chat.domain.ChatMessage;
import br.com.qsoft.governance.chat.domain.Conversation;
import br.com.qsoft.governance.chat.domain.TurnInProgressOrFailedException;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Reconstrói o turno de um {@code clientMessageId} já processado (ADR-42). */
@Service
public class ChatTurnReplay {

    private final ChatMessageRepository messageRepository;
    private final AttemptReceiptQuery receiptQuery;

    public ChatTurnReplay(ChatMessageRepository messageRepository,
                          AttemptReceiptQuery receiptQuery) {
        this.messageRepository = messageRepository;
        this.receiptQuery = receiptQuery;
    }

    public ChatTurn replay(Conversation conversation, ChatMessage userMessage) {
        ChatMessage assistant = messageRepository.findAssistantAfter(
                conversation.tenantId(), conversation.id(), conversation.ownerUserId(),
                userMessage.createdAt())
            .orElseThrow(() -> new TurnInProgressOrFailedException(userMessage.id(), null));
        if (assistant.requestId() == null) {
            throw new TurnInProgressOrFailedException(userMessage.id(), null);
        }

        ReceiptInfo receipt = receiptQuery.receipts(conversation.tenantId(),
                conversation.ownerUserId(), Set.of(assistant.requestId()))
            .get(assistant.requestId());
        CompletionResult completion = receipt == null ? null : new CompletionResult(
            receipt.requestId(), assistant.content(), List.of(), receipt.modelId(),
            receipt.modelName(), null, receipt.providerName(), receipt.inputTokens(),
            receipt.outputTokens(), receipt.estimatedCost(), receipt.costStatus(),
            receipt.latencyMs() == null ? 0 : receipt.latencyMs());
        return new ChatTurn(conversation, userMessage, assistant, completion, true);
    }
}
