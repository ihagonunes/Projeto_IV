package br.com.qsoft.governance.aigateway.domain.provider;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado raiz do catálogo: um fornecedor de IA do tenant (ADR-09).
 * Vários Providers podem estar ACTIVE simultaneamente. O domínio não depende de
 * Spring/JPA (ADR-31).
 */
public final class Provider {

    private static final int NAME_MAX = 150;

    private final UUID id;
    private final TenantId tenantId;
    private String name;
    private ProviderStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Provider(UUID id, TenantId tenantId, String name, ProviderStatus status,
                     Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.name = Objects.requireNonNull(name, "name");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Provider create(UUID id, TenantId tenantId, String name) {
        Instant now = Instant.now();
        return new Provider(id, tenantId, requireName(name), ProviderStatus.ACTIVE, now, now);
    }

    public static Provider restore(UUID id, TenantId tenantId, String name, ProviderStatus status,
                                   Instant createdAt, Instant updatedAt) {
        return new Provider(id, tenantId, name, status, createdAt, updatedAt);
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String name() {
        return name;
    }

    public ProviderStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return status == ProviderStatus.ACTIVE;
    }

    public Provider rename(String newName) {
        this.name = requireName(newName);
        return touch();
    }

    public Provider activate() {
        if (status != ProviderStatus.ACTIVE) {
            this.status = ProviderStatus.ACTIVE;
            touch();
        }
        return this;
    }

    public Provider deactivate() {
        if (status != ProviderStatus.INACTIVE) {
            this.status = ProviderStatus.INACTIVE;
            touch();
        }
        return this;
    }

    private Provider touch() {
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
