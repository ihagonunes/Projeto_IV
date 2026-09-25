package br.com.qsoft.governance.aigateway.domain.model;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Modelo do catálogo do tenant. Referencia o Provider <b>por ID</b> (FK
 * intramódulo — ADR-09/ADR-29) e não conhece o agregado Provider.
 */
public final class Model {

    private static final int NAME_MAX = 150;

    private final UUID id;
    private final TenantId tenantId;
    private final UUID providerId;
    private String name;
    private ModelStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Model(UUID id, TenantId tenantId, UUID providerId, String name, ModelStatus status,
                  Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.name = Objects.requireNonNull(name, "name");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Model create(UUID id, TenantId tenantId, UUID providerId, String name) {
        Instant now = Instant.now();
        return new Model(id, tenantId, providerId, requireName(name), ModelStatus.AVAILABLE, now, now);
    }

    public static Model restore(UUID id, TenantId tenantId, UUID providerId, String name,
                                ModelStatus status, Instant createdAt, Instant updatedAt) {
        return new Model(id, tenantId, providerId, name, status, createdAt, updatedAt);
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public UUID providerId() {
        return providerId;
    }

    public String name() {
        return name;
    }

    public ModelStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public boolean isAvailable() {
        return status == ModelStatus.AVAILABLE;
    }

    public Model rename(String newName) {
        this.name = requireName(newName);
        return touch();
    }

    public Model makeAvailable() {
        if (status != ModelStatus.AVAILABLE) {
            this.status = ModelStatus.AVAILABLE;
            touch();
        }
        return this;
    }

    public Model disable() {
        if (status != ModelStatus.DISABLED) {
            this.status = ModelStatus.DISABLED;
            touch();
        }
        return this;
    }

    private Model touch() {
        this.updatedAt = Instant.now();
        return this;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name é obrigatório.");
        }
        String trimmed = name.trim();
        if (trimmed.length() > NAME_MAX) {
            throw new IllegalArgumentException("name deve ter no máximo " + NAME_MAX + " caracteres.");
        }
        return trimmed;
    }
}
