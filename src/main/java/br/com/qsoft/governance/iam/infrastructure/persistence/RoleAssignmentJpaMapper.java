package br.com.qsoft.governance.iam.infrastructure.persistence;

import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.sharedkernel.id.RoleAssignmentId;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;

/**
 * Converte entre a atribuição de domínio e a entidade JPA.
 */
public final class RoleAssignmentJpaMapper {

    public RoleAssignmentJpaEntity toEntity(RoleAssignment assignment) {
        RoleAssignmentJpaEntity entity = new RoleAssignmentJpaEntity();
        entity.setId(assignment.id().value());
        entity.setTenantId(assignment.tenantId().value());
        entity.setUserId(assignment.userId().value());
        entity.setRoleId(assignment.roleId().value());
        entity.setDepartmentId(assignment.departmentId());
        return entity;
    }

    public RoleAssignment toDomain(RoleAssignmentJpaEntity entity) {
        return new RoleAssignment(
            RoleAssignmentId.of(entity.getId()),
            UserId.of(entity.getUserId()),
            RoleId.of(entity.getRoleId()),
            TenantId.of(entity.getTenantId()),
            entity.getDepartmentId()
        );
    }
}
