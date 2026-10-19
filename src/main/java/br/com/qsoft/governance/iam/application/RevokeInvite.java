package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.InviteRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.rbac.GrantAuthority;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.InvalidInviteException;
import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.iam.domain.invite.InviteStatus;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.SystemRole;
import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso de revogação de convite. O ator só revoga convites cujo papel
 * pode conceder. Convite de outro tenant é tratado como inválido (404 implícito
 * via 400), sem revelar existência.
 */
@Service
public class RevokeInvite {

    private final InviteRepository inviteRepository;
    private final RoleRepository roleRepository;
    private final GrantAuthority grantAuthority;

    public RevokeInvite(InviteRepository inviteRepository,
                        RoleRepository roleRepository,
                        GrantAuthority grantAuthority) {
        this.inviteRepository = inviteRepository;
        this.roleRepository = roleRepository;
        this.grantAuthority = grantAuthority;
    }

    @Transactional
    public void revoke(RevokeInviteCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        InviteId inviteId = Objects.requireNonNull(command.inviteId(), "inviteId");
        UserId actor = Objects.requireNonNull(command.actor(), "actor");

        Invite invite = inviteRepository.findById(inviteId)
            .filter(candidate -> candidate.tenantId().equals(tenantId))
            .orElseThrow(InvalidInviteException::new);

        if (invite.status() == InviteStatus.ACCEPTED) {
            throw new InvalidInviteException();
        }
        if (invite.roleId() == null) {
            throw new InvalidInviteException();
        }
        Role role = roleRepository.findById(RoleId.of(invite.roleId()))
            .orElseThrow(InvalidInviteException::new);
        SystemRole target = role.systemRole().orElseThrow(InvalidInviteException::new);
        if (!grantAuthority.canGrantByInvite(tenantId, actor, target, invite.departmentId())) {
            throw new AuthorizationDeniedException(
                "Ator não pode conceder o papel do convite.");
        }

        invite.revoke();
        inviteRepository.save(invite);
    }
}
