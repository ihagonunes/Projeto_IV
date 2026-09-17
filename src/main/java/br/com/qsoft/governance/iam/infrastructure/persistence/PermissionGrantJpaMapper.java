package br.com.qsoft.governance.iam.infrastructure.persistence;

import br.com.qsoft.governance.iam.domain.permission.PermissionGrant;

/**
 * Converte entre o agregado PermissionGrant e a entidade JPA.
 */
public final class PermissionGrantJpaMapper {

    public PermissionGrantJpaEntity toEntity(PermissionGrant grant) {
        return new PermissionGrantJpaEntity(
            grant.id(),
            grant.roleLevel(),
            grant.resource(),
            grant.action()
        );
    }

    public PermissionGrant toDomain(PermissionGrantJpaEntity entity) {
        return new PermissionGrant(
            entity.getId(),
            entity.getRoleLevel(),
            entity.getResource(),
            entity.getAction()
        );
    }
}