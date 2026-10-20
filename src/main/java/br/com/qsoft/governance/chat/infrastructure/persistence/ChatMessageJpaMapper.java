package br.com.qsoft.governance.chat.infrastructure.persistence;

import br.com.qsoft.governance.chat.domain.ChatMessage;
import br.com.qsoft.governance.sharedkernel.id.TenantId;

/** Converte entre a mensagem de domínio e a entidade JPA. */
public final class ChatMessageJpaMapper {

    public ChatMessageJpaEntity toEntity(ChatMessage message) {
        ChatMessageJpaEntity entity = new ChatMessageJpaEntity();
        entity.setId(message.id());
        entity.setTenantId(message.tenantId().value());
        entity.setConversationId(message.conversationId());
        entity.setRole(message.role());
        entity.setContent(message.content());
        entity.setRequestId(message.requestId());
        entity.setCreatedAt(message.createdAt());
        return entity;
    }

    public ChatMessage toDomain(ChatMessageJpaEntity entity) {
        return ChatMessage.restore(
            entity.getId(),
            TenantId.of(entity.getTenantId()),
            entity.getConversationId(),
            entity.getRole(),
            entity.getContent(),
            entity.getRequestId(),
            entity.getCreatedAt()
        );
    }
}
