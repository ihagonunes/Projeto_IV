package br.com.qsoft.governance.chat.application;

import br.com.qsoft.governance.chat.application.port.ConversationRepository;
import br.com.qsoft.governance.chat.domain.Conversation;
import br.com.qsoft.governance.chat.domain.ConversationStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import org.springframework.stereotype.Service;

/** Lista paginada das conversas do ator, por {@code updated_at} desc. */
@Service
public class ListConversations {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private final ConversationRepository conversationRepository;

    public ListConversations(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    public PageResult<Conversation> list(TenantId tenantId, UserId owner, int page, int size) {
        return list(tenantId, owner, ConversationStatus.ACTIVE, null, page, size);
    }

    public PageResult<Conversation> list(TenantId tenantId, UserId owner,
                                         ConversationStatus status, String titleQuery,
                                         int page, int size) {
        ConversationStatus effective = status == null ? ConversationStatus.ACTIVE : status;
        if (effective == ConversationStatus.DELETED) {
            throw new IllegalArgumentException("status inválido.");
        }
        if (titleQuery != null && titleQuery.length() > 100) {
            throw new IllegalArgumentException("q deve ter no máximo 100 caracteres.");
        }
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return conversationRepository.search(
            tenantId, owner, effective, titleQuery, safePage, safeSize);
    }
}
