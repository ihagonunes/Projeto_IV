package br.com.qsoft.governance.department.infrastructure.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * Persistência JPA da ModelPolicy. {@code default_model_id} e os itens de
 * {@code department_model_policy_allowed_models} são referências cross-module
 * por ID ao catálogo do aigateway, sem FK (ADR-29). A FK da tabela de itens é
 * intramódulo. {@code tenant_id} é referência cross-module por ID, sem FK.
 */
@FilterDef(name = ModelPolicyJpaEntity.TENANT_FILTER_NAME,
    parameters = @ParamDef(name = "tenantId", type = UUID.class))
@Filter(name = ModelPolicyJpaEntity.TENANT_FILTER_NAME, condition = "tenant_id = :tenantId")
@Entity
@Table(name = "department_model_policies")
public class ModelPolicyJpaEntity {

    public static final String TENANT_FILTER_NAME = "modelPolicyTenantFilter";

    @Id
    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "default_model_id")
    private UUID defaultModelId;

    @ElementCollection
    @CollectionTable(name = "department_model_policy_allowed_models",
        joinColumns = @JoinColumn(name = "department_id"))
    @Column(name = "model_id", nullable = false)
    private Set<UUID> allowedModelIds = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ModelPolicyJpaEntity() {
        // para JPA
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getDefaultModelId() {
        return defaultModelId;
    }

    public void setDefaultModelId(UUID defaultModelId) {
        this.defaultModelId = defaultModelId;
    }

    public Set<UUID> getAllowedModelIds() {
        return allowedModelIds;
    }

    public void setAllowedModelIds(Set<UUID> allowedModelIds) {
        this.allowedModelIds = allowedModelIds == null ? new HashSet<>() : new HashSet<>(allowedModelIds);
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
