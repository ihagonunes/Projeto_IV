package br.com.qsoft.governance.department.domain;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado raiz do módulo {@code department}: um departamento do tenant.
 * <p>
 * {@code leaderUserId} é uma referência <b>cross-module por ID</b> (o usuário
 * pertence ao IAM), sem associação JPA/FK (ADR-29). {@code monthlyBudget}
 * materializa o {@code BudgetLimit} mensal, cuja fonte de verdade é o
 * department (ADR-06). O domínio não depende de Spring/JPA (ADR-31).
 */
public final class Department {

    private static final int NAME_MAX = 150;
    private static final int DESCRIPTION_MAX = 500;

    private final UUID id;
    private final TenantId tenantId;
    private String name;
    private String description;
    private UUID leaderUserId;
    private BigDecimal monthlyBudget;
    private DepartmentStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Department(UUID id, TenantId tenantId, String name, String description,
                       UUID leaderUserId, BigDecimal monthlyBudget, DepartmentStatus status,
                       Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.name = Objects.requireNonNull(name, "name");
        this.description = description;
        this.leaderUserId = leaderUserId;
        this.monthlyBudget = monthlyBudget;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Department create(UUID id, TenantId tenantId, String name, String description,
                                    UUID leaderUserId, BigDecimal monthlyBudget) {
        Instant now = Instant.now();
        return new Department(id, tenantId, requireName(name),
            normalizeDescription(description), leaderUserId, requireBudget(monthlyBudget),
            DepartmentStatus.ACTIVE, now, now);
    }

    public static Department restore(UUID id, TenantId tenantId, String name, String description,
                                     UUID leaderUserId, BigDecimal monthlyBudget,
                                     DepartmentStatus status, Instant createdAt, Instant updatedAt) {
        return new Department(id, tenantId, name, description, leaderUserId, monthlyBudget,
            status, createdAt, updatedAt);
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

    public String description() {
        return description;
    }

    public UUID leaderUserId() {
        return leaderUserId;
    }

    public BigDecimal monthlyBudget() {
        return monthlyBudget;
    }

    public DepartmentStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Department rename(String newName) {
        this.name = requireName(newName);
        return touch();
    }

    public Department changeDescription(String newDescription) {
        this.description = normalizeDescription(newDescription);
        return touch();
    }

    /** Define (ou remove, com {@code null}) o líder do departamento. */
    public Department changeLeader(UUID newLeaderUserId) {
        this.leaderUserId = newLeaderUserId;
        return touch();
    }

    /** Define (ou remove, com {@code null}) o orçamento mensal. */
    public Department changeMonthlyBudget(BigDecimal newBudget) {
        this.monthlyBudget = requireBudget(newBudget);
        return touch();
    }

    public Department deactivate() {
        if (status != DepartmentStatus.INACTIVE) {
            this.status = DepartmentStatus.INACTIVE;
            touch();
        }
        return this;
    }

    public Department activate() {
        if (status != DepartmentStatus.ACTIVE) {
            this.status = DepartmentStatus.ACTIVE;
            touch();
        }
        return this;
    }

    private Department touch() {
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

    private static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > DESCRIPTION_MAX) {
            throw new IllegalArgumentException(
                "description deve ter no máximo " + DESCRIPTION_MAX + " caracteres.");
        }
        return trimmed;
    }

    private static BigDecimal requireBudget(BigDecimal budget) {
        if (budget == null) {
            return null;
        }
        if (budget.signum() < 0) {
            throw new IllegalArgumentException("monthlyBudget não pode ser negativo.");
        }
        return budget;
    }
}
