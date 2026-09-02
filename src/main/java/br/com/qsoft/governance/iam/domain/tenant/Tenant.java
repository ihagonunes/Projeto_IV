package br.com.qsoft.governance.iam.domain.tenant;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate raiz do módulo IAM que representa a organização cliente.
 * É a raiz do tenant; portanto não possui um tenant_id próprio.
 */
public final class Tenant {

    private final TenantId id;
    private final String slug;
    private final String name;
    private TenantStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Tenant(TenantId id, String slug, String name, TenantStatus status,
                   Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.slug = Objects.requireNonNull(slug, "slug");
        this.name = Objects.requireNonNull(name, "name");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Tenant create(TenantId id, String slug, String name) {
        Instant now = Instant.now();
        return new Tenant(id, slug, name, TenantStatus.ACTIVE, now, now);
    }

    public static Tenant restore(TenantId id, String slug, String name,
                                 TenantStatus status, Instant createdAt, Instant updatedAt) {
        return new Tenant(id, slug, name, status, createdAt, updatedAt);
    }

    public TenantId id() {
        return id;
    }

    public String slug() {
        return slug;
    }

    public String name() {
        return name;
    }

    public TenantStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Tenant activate() {
        if (status != TenantStatus.ACTIVE) {
            this.status = TenantStatus.ACTIVE;
            this.updatedAt = Instant.now();
        }
        return this;
    }

    public Tenant deactivate() {
        if (status != TenantStatus.INACTIVE) {
            this.status = TenantStatus.INACTIVE;
            this.updatedAt = Instant.now();
        }
        return this;
    }
}
