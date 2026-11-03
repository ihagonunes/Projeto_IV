package br.com.qsoft.governance.chat.domain;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Mensagem de chat (ADR-39). Conteúdo de 1 a 8000 caracteres. O
 * {@code requestId} só existe na mensagem ASSISTANT e liga o recibo à
 * tentativa/UsageRecord.
 */
public final class ChatMessage {

    public static final int CONTENT_MIN = 1;
    public static final int CONTENT_MAX = 8000;

    private final UUID id;
    private final TenantId tenantId;
    private final UUID conversationId;
    private final MessageRole role;
    private final String content;
    private final UUID requestId;
    private final String clientMessageId;
    private final Instant createdAt;

    private ChatMessage(UUID id, TenantId tenantId, UUID conversationId, MessageRole role,
                        String content, UUID requestId, String clientMessageId,
                        Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId");
        this.role = Objects.requireNonNull(role, "role");
        this.content = requireContent(content);
        this.requestId = requestId;
        this.clientMessageId = requireClientMessageId(clientMessageId);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static ChatMessage user(UUID id, TenantId tenantId, UUID conversationId,
                                   String content) {
        return user(id, tenantId, conversationId, content, null);
    }

    public static ChatMessage user(UUID id, TenantId tenantId, UUID conversationId,
                                   String content, String clientMessageId) {
        return new ChatMessage(id, tenantId, conversationId, MessageRole.USER, content, null,
            clientMessageId, Instant.now());
    }

    public static ChatMessage assistant(UUID id, TenantId tenantId, UUID conversationId,
                                        String content, UUID requestId) {
        return new ChatMessage(id, tenantId, conversationId, MessageRole.ASSISTANT, content,
            Objects.requireNonNull(requestId, "requestId é obrigatório na resposta."), null,
            Instant.now());
    }

    public static ChatMessage restore(UUID id, TenantId tenantId, UUID conversationId,
                                      MessageRole role, String content, UUID requestId,
                                      Instant createdAt) {
        return restore(id, tenantId, conversationId, role, content, requestId, null, createdAt);
    }

    public static ChatMessage restore(UUID id, TenantId tenantId, UUID conversationId,
                                      MessageRole role, String content, UUID requestId,
                                      String clientMessageId, Instant createdAt) {
        return new ChatMessage(id, tenantId, conversationId, role, content, requestId,
            clientMessageId, createdAt);
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public UUID conversationId() {
        return conversationId;
    }

    public MessageRole role() {
        return role;
    }

    public String content() {
        return content;
    }

    public UUID requestId() {
        return requestId;
    }

    public String clientMessageId() {
        return clientMessageId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    private static String requireClientMessageId(String clientMessageId) {
        if (clientMessageId == null || clientMessageId.isBlank()) {
            return null;
        }
        if (!clientMessageId.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new IllegalArgumentException(
                "clientMessageId deve ter 8 a 64 caracteres [A-Za-z0-9_-].");
        }
        return clientMessageId;
    }

    private static String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("mensagem é obrigatória.");
        }
        if (content.length() > CONTENT_MAX) {
            throw new IllegalArgumentException(
                "mensagem deve ter no máximo " + CONTENT_MAX + " caracteres.");
        }
        return content;
    }
}
