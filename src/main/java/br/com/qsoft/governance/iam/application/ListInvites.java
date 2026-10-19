package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.InviteRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.rbac.GrantAuthority;
import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.SystemRole;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de listagem dos convites do tenant (SPI-59/POPROJ-14), restrita
 * aos convites cujo papel o ator pode conceder (ADR-37).
 */
@Service
public class ListInvites {

    private final InviteRepository inviteRepository;
    private final RoleRepository roleRepository;
    private final GrantAuthority grantAuthority;

    public ListInvites(InviteRepository inviteRepository,
                       RoleRepository roleRepository,
                       GrantAuthority grantAuthority) {
        this.inviteRepository = inviteRepository;
        this.roleRepository = roleRepository;
        this.grantAuthority = grantAuthority;
    }

    public List<Invite> list(TenantId tenantId, UserId actor) {
        TenantId id = Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(actor, "actor");
        return inviteRepository.findByTenantId(id).stream()
            .filter(invite -> canGrant(tenantId, actor, invite))
            .toList();
    }

    private boolean canGrant(TenantId tenantId, UserId actor, Invite invite) {
        if (invite.roleId() == null) {
            return false;
        }
        Role role = roleRepository.findById(RoleId.of(invite.roleId())).orElse(null);
        if (role == null) {
            return false;
        }
        SystemRole target = role.systemRole().orElse(null);
        return target != null
            && grantAuthority.canGrantByInvite(tenantId, actor, target, invite.departmentId());
    }
}
