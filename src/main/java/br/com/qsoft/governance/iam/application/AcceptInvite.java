package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.InviteRepository;
import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.rbac.GrantAuthority;
import br.com.qsoft.governance.iam.domain.exception.InvalidInviteException;
import br.com.qsoft.governance.iam.domain.exception.InviteAuthorityLostException;
import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.iam.domain.rbac.GrantOrigin;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.iam.domain.rbac.SystemRole;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso de aceite do convite (ADR-37): valida o token (pendente e não
 * expirado), revalida a autoridade ATUAL do convidante e, se ainda válida, cria
 * o usuário (lotação = departamento do convite) e a concessão (origem
 * {@code INVITE}, {@code grantedBy} = convidante) na mesma transação. Token
 * inválido/expirado/reutilizado → 400; convidante sem autoridade → 409.
 */
@Service
public class AcceptInvite {

    private final InviteRepository inviteRepository;
    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final GrantAuthority grantAuthority;
    private final ProvisionUser provisionUser;

    public AcceptInvite(InviteRepository inviteRepository,
                        RoleRepository roleRepository,
                        RoleAssignmentRepository roleAssignmentRepository,
                        GrantAuthority grantAuthority,
                        ProvisionUser provisionUser) {
        this.inviteRepository = inviteRepository;
        this.roleRepository = roleRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.grantAuthority = grantAuthority;
        this.provisionUser = provisionUser;
    }

    @Transactional
    public User accept(AcceptInviteCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        String token = requireNonBlank(command.token(), "token");
        String password = requireNonBlank(command.password(), "password");
        String name = (command.name() == null || command.name().isBlank())
            ? null : command.name().trim();

        Invite invite = inviteRepository.findByTokenHash(InviteTokenHasher.sha256Hex(token))
            .orElseThrow(InvalidInviteException::new);

        // Rejeita reutilização e convite expirado.
        invite.accept(Instant.now());

        if (invite.roleId() == null || invite.invitedBy() == null) {
            throw new InvalidInviteException();
        }
        Role role = roleRepository.findById(RoleId.of(invite.roleId()))
            .orElseThrow(InvalidInviteException::new);
        SystemRole target = role.systemRole().orElseThrow(InvalidInviteException::new);
        UserId inviter = UserId.of(invite.invitedBy());

        // Revalida a delegação com as concessões ATUAIS do convidante.
        if (!grantAuthority.canGrantByInvite(invite.tenantId(), inviter, target, invite.departmentId())) {
            throw new InviteAuthorityLostException();
        }

        User user = provisionUser.provision(new ProvisionUserCommand(
            invite.tenantId(),
            invite.email(),
            name != null ? name : invite.email(),
            invite.hierarchyLevel(),
            password,
            invite.departmentId()
        ));

        roleAssignmentRepository.save(RoleAssignment.grant(
            user.id(), role, invite.departmentId(), GrantOrigin.INVITE, inviter, Instant.now()));

        inviteRepository.save(invite);
        return user;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório.");
        }
        return value.trim();
    }
}
