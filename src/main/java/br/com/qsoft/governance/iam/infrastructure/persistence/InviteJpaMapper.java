package br.com.qsoft.governance.iam.infrastructure.persistence;

import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;

/**
 * Converte entre o convite de domínio e a entidade JPA.
 */
public final class InviteJpaMapper {

    public InviteJpaEntity toEntity(Invite invite) {
        InviteJpaEntity entity = new InviteJpaEntity();
        entity.setId(invite.id().value());
        entity.setTenantId(invite.tenantId().value());
        entity.setEmail(invite.email());
        entity.setHierarchyLevel(invite.hierarchyLevel());
        entity.setDepartmentId(invite.departmentId());
        entity.setRoleId(invite.roleId());
        entity.setInvitedBy(invite.invitedBy());
        entity.setTokenHash(invite.tokenHash());
        entity.setStatus(invite.status());
        entity.setCreatedAt(invite.createdAt());
        entity.setExpiresAt(invite.expiresAt());
        entity.setAcceptedAt(invite.acceptedAt());
        return entity;
    }

    public Invite toDomain(InviteJpaEntity entity) {
        return Invite.restore(
            InviteId.of(entity.getId()),
            TenantId.of(entity.getTenantId()),
            entity.getEmail(),
            entity.getHierarchyLevel(),
            entity.getDepartmentId(),
            entity.getRoleId(),
            entity.getInvitedBy(),
            entity.getTokenHash(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getExpiresAt(),
            entity.getAcceptedAt()
        );
    }
}
