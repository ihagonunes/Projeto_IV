package br.com.qsoft.governance.chat.infrastructure.web;

import br.com.qsoft.governance.chat.domain.Conversation;
import java.time.Instant;
import java.util.UUID;

/** Representação pública da conversa. */
public record ConversationResponse(
    UUID id,
    String titulo,
    String status,
    Instant createdAt,
    Instant updatedAt
) {

    public static ConversationResponse from(Conversation conversation) {
        return new ConversationResponse(
            conversation.id(),
            conversation.title(),
            conversation.status().name(),
            conversation.createdAt(),
            conversation.updatedAt()
        );
    }
}
