package br.com.qsoft.governance.iam.domain.user;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate de usuário do módulo IAM. Pertence a um Tenant e pode estar
 * associado a um Department (referência por ID, conforme ADR-05).
 * <p>
 * A autorização vem das concessões de papel ({@code iam_role_assignments}),
 * nunca de um conjunto de permissões no usuário (ADR-36).
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
    private UserStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(UserId id, TenantId tenantId, String email, String name,
                 HierarchyLevel hierarchyLevel, UUID departmentId, String passwordHash,
                 UserStatus status, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.email = Objects.requireNonNull(email, "email");
        this.name = Objects.requireNonNull(name, "name");
        this.hierarchyLevel = Objects.requireNonNull(hierarchyLevel, "hierarchyLevel");
        this.departmentId = departmentId;
        this.passwordHash = passwordHash;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static User create(UserId id, TenantId tenantId, String email, String name,
                              HierarchyLevel hierarchyLevel, UUID departmentId) {
        Instant now = Instant.now();
        return new User(id, tenantId, email, name, hierarchyLevel, departmentId, null,
            UserStatus.ACTIVE, now, now);
    }

    public static User restore(UserId id, TenantId tenantId, String email, String name,
                               HierarchyLevel hierarchyLevel, UUID departmentId, String passwordHash,
                               UserStatus status, Instant createdAt, Instant updatedAt) {
        return new User(id, tenantId, email, name, hierarchyLevel, departmentId, passwordHash,
            status, createdAt, updatedAt);
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

    /**
     * Altera o rótulo hierárquico do usuário. O rótulo é organizacional e não
     * tem efeito de autorização (ADR-36 item 9).
     */
    public User changeHierarchyLevel(HierarchyLevel newLevel) {
        if (newLevel == null) {
            throw new IllegalArgumentException("hierarchyLevel não pode ser nulo.");
        }
        this.hierarchyLevel = newLevel;
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Altera o Department (referência cross-module por ID, ADR-05).
     * {@code null} remove a associação com qualquer Department.
     */
    public User changeDepartment(UUID newDepartmentId) {
        this.departmentId = newDepartmentId;
        this.updatedAt = Instant.now();
        return this;
    }
}
