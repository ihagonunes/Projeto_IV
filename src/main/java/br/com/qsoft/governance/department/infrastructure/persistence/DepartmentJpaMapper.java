package br.com.qsoft.governance.department.infrastructure.persistence;

import br.com.qsoft.governance.department.domain.Department;
import br.com.qsoft.governance.sharedkernel.id.TenantId;

/**
 * Converte entre o agregado Department e a entidade JPA.
 */
public final class DepartmentJpaMapper {

    public DepartmentJpaEntity toEntity(Department department) {
        DepartmentJpaEntity entity = new DepartmentJpaEntity();
        entity.setId(department.id());
        entity.setTenantId(department.tenantId().value());
        entity.setName(department.name());
        entity.setDescription(department.description());
        entity.setLeaderUserId(department.leaderUserId());
        entity.setMonthlyBudget(department.monthlyBudget());
        entity.setStatus(department.status());
        entity.setCreatedAt(department.createdAt());
        entity.setUpdatedAt(department.updatedAt());
        return entity;
    }

    public Department toDomain(DepartmentJpaEntity entity) {
        return Department.restore(
            entity.getId(),
            TenantId.of(entity.getTenantId()),
            entity.getName(),
            entity.getDescription(),
            entity.getLeaderUserId(),
            entity.getMonthlyBudget(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
