package br.com.qsoft.governance.department.infrastructure.persistence;

import br.com.qsoft.governance.department.domain.DepartmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * Persistência JPA do agregado Department.
 * <p>
 * {@code tenant_id} e {@code leader_user_id} são referências <b>cross-module
 * por ID</b>, sem FK SQL (ADR-29). O {@code @Filter} transversal é a DEFESA
 * SECUNDÁRIA de isolamento (ADR-03); a defesa primária é o predicado explícito
 * por {@code tenant_id} nas queries.
 */
@FilterDef(name = DepartmentJpaEntity.TENANT_FILTER_NAME,
    parameters = @ParamDef(name = "tenantId", type = UUID.class))
@Filter(name = DepartmentJpaEntity.TENANT_FILTER_NAME, condition = "tenant_id = :tenantId")
@Entity
@Table(name = "department_departments")
public class DepartmentJpaEntity {

    public static final String TENANT_FILTER_NAME = "departmentTenantFilter";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "leader_user_id")
    private UUID leaderUserId;

    @Column(name = "monthly_budget", precision = 15, scale = 2)
    private BigDecimal monthlyBudget;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DepartmentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DepartmentJpaEntity() {
        // para JPA
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getLeaderUserId() {
        return leaderUserId;
    }

    public void setLeaderUserId(UUID leaderUserId) {
        this.leaderUserId = leaderUserId;
    }

    public BigDecimal getMonthlyBudget() {
        return monthlyBudget;
    }

    public void setMonthlyBudget(BigDecimal monthlyBudget) {
        this.monthlyBudget = monthlyBudget;
    }

    public DepartmentStatus getStatus() {
        return status;
    }

    public void setStatus(DepartmentStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
