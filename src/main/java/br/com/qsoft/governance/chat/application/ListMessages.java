package br.com.qsoft.governance.chat.application;

import br.com.qsoft.governance.aigateway.api.model.ReceiptInfo;
import br.com.qsoft.governance.aigateway.api.query.AttemptReceiptQuery;
import br.com.qsoft.governance.chat.application.port.ChatMessageRepository;
import br.com.qsoft.governance.chat.application.port.ConversationRepository;
import br.com.qsoft.governance.chat.domain.ChatMessage;
import br.com.qsoft.governance.chat.domain.ConversationNotFoundException;
import br.com.qsoft.governance.chat.domain.MessageRole;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Mensagens da conversa do próprio ator, em ordem crescente, com recibo em
 * cada ASSISTANT (ADR-42). Outra pessoa ou DELETED → 404; arquivada continua
 * legível.
 */
@Service
public class ListMessages {

    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 200;
    private static final int RECEIPT_BATCH = 100;

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final AttemptReceiptQuery receiptQuery;

    public ListMessages(ConversationRepository conversationRepository,
                        ChatMessageRepository messageRepository,
                        AttemptReceiptQuery receiptQuery) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.receiptQuery = receiptQuery;
    }

    public List<ChatMessageView> list(TenantId tenantId, UserId owner, UUID conversationId) {
        return list(tenantId, owner, conversationId, 0, DEFAULT_SIZE);
    }

    public List<ChatMessageView> list(TenantId tenantId, UserId owner, UUID conversationId,
                                      int page, int size) {
        conversationRepository.findByTenantIdAndIdAndOwner(tenantId, conversationId, owner)
            .orElseThrow(ConversationNotFoundException::new);

        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        List<ChatMessage> messages = messageRepository.findPage(
            tenantId, conversationId, owner, safePage, safeSize);

        List<UUID> requestIds = messages.stream()
            .filter(message -> message.role() == MessageRole.ASSISTANT)
            .map(ChatMessage::requestId)
            .filter(java.util.Objects::nonNull)
            .toList();
        Map<UUID, ReceiptInfo> receipts = new HashMap<>();
        for (int start = 0; start < requestIds.size(); start += RECEIPT_BATCH) {
            Set<UUID> batch = new HashSet<>(requestIds.subList(
                start, Math.min(start + RECEIPT_BATCH, requestIds.size())));
            receipts.putAll(receiptQuery.receipts(tenantId, owner, batch));
        }

        List<ChatMessageView> views = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            ReceiptInfo receipt = message.role() == MessageRole.ASSISTANT
                ? receipts.get(message.requestId()) : null;
            views.add(new ChatMessageView(message, receipt));
        }
        return views;
    }
}
