package br.com.qsoft.governance.iam.domain.rbac;

import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Papel (Role) de autorização, pertencente a um tenant, composto por um
 * conjunto de permissões granulares (recurso × ação).
 * O domínio não depende de Spring/JPA (ADR-31).
 */
public final class Role {

    private final RoleId id;
    private final TenantId tenantId;
    private String name;
    private final Set<Permission> permissions;
    private final Instant createdAt;
    private Instant updatedAt;

    private Role(RoleId id, TenantId tenantId, String name, Set<Permission> permissions,
                 Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.name = requireName(name);
        this.permissions = permissions == null ? new HashSet<>() : new HashSet<>(permissions);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Role create(RoleId id, TenantId tenantId, String name) {
        Instant now = Instant.now();
        return new Role(id, tenantId, name, Set.of(), now, now);
    }

    public static Role restore(RoleId id, TenantId tenantId, String name,
                               Set<Permission> permissions, Instant createdAt, Instant updatedAt) {
        return new Role(id, tenantId, name, permissions, createdAt, updatedAt);
    }

    public RoleId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String name() {
        return name;
    }

    public Set<Permission> permissions() {
        return Set.copyOf(permissions);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    public Role rename(String newName) {
        this.name = requireName(newName);
        this.updatedAt = Instant.now();
        return this;
    }

    public Role grantPermission(Permission permission) {
        Objects.requireNonNull(permission, "permission não pode ser nula.");
        permissions.add(permission);
        this.updatedAt = Instant.now();
        return this;
    }

    public Role revokePermission(Permission permission) {
        Objects.requireNonNull(permission, "permission não pode ser nula.");
        permissions.remove(permission);
        this.updatedAt = Instant.now();
        return this;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name é obrigatório.");
        }
        return name.trim();
    }
}
