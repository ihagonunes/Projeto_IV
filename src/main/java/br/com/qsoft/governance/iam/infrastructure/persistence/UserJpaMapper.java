package br.com.qsoft.governance.iam.infrastructure.persistence;

import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;

/**
 * Converte entre o agregado User e a entidade JPA.
 */
public final class UserJpaMapper {

    public UserJpaEntity toEntity(User user) {
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(user.id().value());
        entity.setTenantId(user.tenantId().value());
        entity.setEmail(user.email());
        entity.setName(user.name());
        entity.setHierarchyLevel(user.hierarchyLevel());
        entity.setDepartmentId(user.departmentId());
        entity.setStatus(user.status());
        entity.setCreatedAt(user.createdAt());
        entity.setUpdatedAt(user.updatedAt());
        return entity;
    }

    public User toDomain(UserJpaEntity entity) {
        return User.restore(
            UserId.of(entity.getId()),
            TenantId.of(entity.getTenantId()),
            entity.getEmail(),
            entity.getName(),
            entity.getHierarchyLevel(),
            entity.getDepartmentId(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
