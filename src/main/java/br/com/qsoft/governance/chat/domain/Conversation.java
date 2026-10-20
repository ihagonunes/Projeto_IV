package br.com.qsoft.governance.chat.domain;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Conversa privada de um usuário (ADR-39). O título são os primeiros 60
 * caracteres da primeira mensagem. Sempre pertence a um único dono.
 */
public final class Conversation {

    static final int TITLE_MAX = 60;

    private final UUID id;
    private final TenantId tenantId;
    private final UserId ownerUserId;
    private final String title;
    private ConversationStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Conversation(UUID id, TenantId tenantId, UserId ownerUserId, String title,
                         ConversationStatus status, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.ownerUserId = Objects.requireNonNull(ownerUserId, "ownerUserId");
        this.title = requireTitle(title);
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Conversation start(UUID id, TenantId tenantId, UserId ownerUserId,
                                     String firstMessage) {
        Instant now = Instant.now();
        return new Conversation(id, tenantId, ownerUserId, titleFrom(firstMessage),
            ConversationStatus.ACTIVE, now, now);
    }

    public static Conversation restore(UUID id, TenantId tenantId, UserId ownerUserId,
                                       String title, ConversationStatus status,
                                       Instant createdAt, Instant updatedAt) {
        return new Conversation(id, tenantId, ownerUserId, title, status, createdAt, updatedAt);
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public UserId ownerUserId() {
        return ownerUserId;
    }

    public String title() {
        return title;
    }

    public ConversationStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public boolean isOwnedBy(UserId userId) {
        return ownerUserId.equals(userId);
    }

    public Conversation archive() {
        this.status = ConversationStatus.ARCHIVED;
        return touch();
    }

    public Conversation touch() {
        this.updatedAt = Instant.now();
        return this;
    }

    private static String titleFrom(String firstMessage) {
        String text = firstMessage == null ? "" : firstMessage.trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) {
            throw new IllegalArgumentException("mensagem é obrigatória.");
        }
        String title = text.length() <= TITLE_MAX ? text : text.substring(0, TITLE_MAX);
        return title;
    }

    private static String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("título é obrigatório.");
        }
        if (title.length() > 200) {
            throw new IllegalArgumentException("título deve ter no máximo 200 caracteres.");
        }
        return title;
    }
}
