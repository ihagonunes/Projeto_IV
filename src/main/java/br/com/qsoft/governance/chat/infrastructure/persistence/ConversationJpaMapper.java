package br.com.qsoft.governance.chat.infrastructure.persistence;

import br.com.qsoft.governance.chat.domain.Conversation;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;

/** Converte entre a conversa de domínio e a entidade JPA. */
public final class ConversationJpaMapper {

    public ConversationJpaEntity toEntity(Conversation conversation) {
        ConversationJpaEntity entity = new ConversationJpaEntity();
        entity.setId(conversation.id());
        entity.setTenantId(conversation.tenantId().value());
        entity.setOwnerUserId(conversation.ownerUserId().value());
        entity.setTitle(conversation.title());
        entity.setStatus(conversation.status());
        entity.setCreatedAt(conversation.createdAt());
        entity.setUpdatedAt(conversation.updatedAt());
        return entity;
    }

    public Conversation toDomain(ConversationJpaEntity entity) {
        return Conversation.restore(
            entity.getId(),
            TenantId.of(entity.getTenantId()),
            UserId.of(entity.getOwnerUserId()),
            entity.getTitle(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
