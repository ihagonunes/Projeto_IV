package br.com.qsoft.governance.iam.domain.user;

import br.com.qsoft.governance.iam.api.model.Permission;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregate de usuário do módulo IAM. Pertence a um Tenant e pode estar
 * associado a um Department (referência por ID, conforme ADR-05).
 * O domínio não depende de JPA/Spring (ADR-31).
 */
public final class User {

    private final UserId id;
    private final TenantId tenantId;
    private final String email;
    private final String name;
    private HierarchyLevel hierarchyLevel;
    private UUID departmentId;
    private String passwordHash;
    private final Set<Permission> permissions;
    private UserStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(UserId id, TenantId tenantId, String email, String name,
                 HierarchyLevel hierarchyLevel, UUID departmentId, String passwordHash,
                 Set<Permission> permissions, UserStatus status, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.email = Objects.requireNonNull(email, "email");
        this.name = Objects.requireNonNull(name, "name");
        this.hierarchyLevel = Objects.requireNonNull(hierarchyLevel, "hierarchyLevel");
        this.departmentId = departmentId;
        this.passwordHash = passwordHash;
        this.permissions = permissions == null ? new HashSet<>() : new HashSet<>(permissions);
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static User create(UserId id, TenantId tenantId, String email, String name,
                              HierarchyLevel hierarchyLevel, UUID departmentId) {
        Instant now = Instant.now();
        return new User(id, tenantId, email, name, hierarchyLevel, departmentId, null,
            Set.of(), UserStatus.ACTIVE, now, now);
    }

    public static User restore(UserId id, TenantId tenantId, String email, String name,
                               HierarchyLevel hierarchyLevel, UUID departmentId, String passwordHash,
                               Set<Permission> permissions, UserStatus status,
                               Instant createdAt, Instant updatedAt) {
        return new User(id, tenantId, email, name, hierarchyLevel, departmentId, passwordHash,
            permissions, status, createdAt, updatedAt);
    }

    public UserId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String email() {
        return email;
    }

    public String name() {
        return name;
    }

    public HierarchyLevel hierarchyLevel() {
        return hierarchyLevel;
    }

    public UUID departmentId() {
        return departmentId;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public boolean hasPasswordHash() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public User setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.updatedAt = Instant.now();
        return this;
    }

    public Set<Permission> permissions() {
        return Set.copyOf(permissions);
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    /**
     * ADMIN é o conjunto completo de permissões, não um nível hierárquico (ADR-04).
     */
    public boolean isAdmin() {
        return permissions.containsAll(EnumSet.allOf(Permission.class));
    }

    public User grantPermission(Permission permission) {
        if (permission != null) {
            permissions.add(permission);
            this.updatedAt = Instant.now();
        }
        return this;
    }

    public User revokePermission(Permission permission) {
        if (permission != null) {
            permissions.remove(permission);
            this.updatedAt = Instant.now();
        }
        return this;
    }

    public UserStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public User activate() {
        if (status != UserStatus.ACTIVE) {
            this.status = UserStatus.ACTIVE;
            this.updatedAt = Instant.now();
        }
        return this;
    }

    public User deactivate() {
        if (status != UserStatus.INACTIVE) {
            this.status = UserStatus.INACTIVE;
            this.updatedAt = Instant.now();
        }
        return this;
    }

    public User updateDepartment(UUID newDepartmentId) {
        this.departmentId = newDepartmentId;
        this.updatedAt = Instant.now();
        return this;
    }

    public User updateAdminPermissions(boolean makeAdmin) {
        if (makeAdmin) {
            this.permissions.addAll(EnumSet.allOf(Permission.class));
        } else {
            this.permissions.clear();
        }
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Altera o nível hierárquico do usuário. Exige um nível válido e não-nulo;
     * ADMIN não é um nível hierárquico (ADR-04).
     */
    public User changeHierarchyLevel(HierarchyLevel newLevel) {
        if (newLevel == null) {
            throw new IllegalArgumentException("hierarchyLevel não pode ser nulo.");
        }
        this.hierarchyLevel = newLevel;
        this.updatedAt = Instant.now();
        return this;
    }
}
