package br.com.qsoft.governance.agentpolicies.domain;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Documento de conhecimento (ADR-25/ADR-35). É visível para todo o tenant ou
 * apenas para os departamentos em {@code scopes}. Não carrega conteúdo: os
 * trechos vivem nas versões.
 */
public final class KnowledgeDocument {

    private static final int TITLE_MAX = 200;

    private final UUID id;
    private final TenantId tenantId;
    private final String title;
    private final boolean tenantWide;
    private final Set<UUID> departmentIds;
    private final Instant createdAt;
    private final Instant updatedAt;

    private KnowledgeDocument(UUID id, TenantId tenantId, String title, boolean tenantWide,
                              Set<UUID> departmentIds, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.title = requireTitle(title);
        this.tenantWide = tenantWide;
        this.departmentIds = departmentIds == null ? Set.of() : Set.copyOf(departmentIds);
        if (!tenantWide && this.departmentIds.isEmpty()) {
            throw new IllegalArgumentException(
                "documento não tenant-wide exige ao menos um departamento.");
        }
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static KnowledgeDocument create(UUID id, TenantId tenantId, String title,
                                           boolean tenantWide, Set<UUID> departmentIds) {
        Instant now = Instant.now();
        return new KnowledgeDocument(id, tenantId, title, tenantWide, departmentIds, now, now);
    }

    public static KnowledgeDocument restore(UUID id, TenantId tenantId, String title,
                                            boolean tenantWide, Set<UUID> departmentIds,
                                            Instant createdAt, Instant updatedAt) {
        return new KnowledgeDocument(id, tenantId, title, tenantWide, departmentIds,
            createdAt, updatedAt);
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String title() {
        return title;
    }

    public boolean isTenantWide() {
        return tenantWide;
    }

    public Set<UUID> departmentIds() {
        return departmentIds;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /** Visível se for tenant-wide ou se a lotação estiver no escopo. */
    public boolean isVisibleTo(UUID departmentId) {
        return tenantWide || (departmentId != null && departmentIds.contains(departmentId));
    }

    private static String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("título é obrigatório.");
        }
        String trimmed = title.trim();
        if (trimmed.length() > TITLE_MAX) {
            throw new IllegalArgumentException(
                "título deve ter no máximo " + TITLE_MAX + " caracteres.");
        }
        return trimmed;
    }
}
