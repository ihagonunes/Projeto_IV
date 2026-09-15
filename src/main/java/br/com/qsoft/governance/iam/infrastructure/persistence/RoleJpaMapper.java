package br.com.qsoft.governance.iam.infrastructure.persistence;

import br.com.qsoft.governance.iam.domain.rbac.Permission;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converte entre o agregado Role e a entidade JPA.
 */
public final class RoleJpaMapper {

    public RoleJpaEntity toEntity(Role role) {
        RoleJpaEntity entity = new RoleJpaEntity();
        entity.setId(role.id().value());
        entity.setTenantId(role.tenantId().value());
        entity.setName(role.name());
        Set<RolePermissionEmbeddable> permissions = role.permissions().stream()
            .map(p -> new RolePermissionEmbeddable(p.resource(), p.action()))
            .collect(Collectors.toCollection(HashSet::new));
        entity.setPermissions(permissions);
        entity.setCreatedAt(role.createdAt());
        entity.setUpdatedAt(role.updatedAt());
        return entity;
    }

    public Role toDomain(RoleJpaEntity entity) {
        Set<Permission> permissions = entity.getPermissions().stream()
            .map(p -> Permission.of(p.getResource(), p.getAction()))
            .collect(Collectors.toCollection(HashSet::new));
        return Role.restore(
            RoleId.of(entity.getId()),
            TenantId.of(entity.getTenantId()),
            entity.getName(),
            permissions,
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
