package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.InviteRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.application.rbac.GrantAuthority;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.InvalidInviteException;
import br.com.qsoft.governance.iam.domain.exception.InviteNotFoundException;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.iam.domain.invite.InviteStatus;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.SystemRole;
import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso de reenvio de convite (SPI-59): gera um NOVO token (SHA-256
 * persistido) e nova expiração a partir de um convite existente não aceito,
 * revogando o anterior. O ator precisa poder conceder o papel do convite.
 */
@Service
public class ResendInvite {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final InviteRepository inviteRepository;
    private final GrantAuthority grantAuthority;
    private final long ttlHours;

    public ResendInvite(UserRepository userRepository,
                        RoleRepository roleRepository,
                        InviteRepository inviteRepository,
                        GrantAuthority grantAuthority,
                        @Value("${app.security.invite.ttl-hours:48}") long ttlHours) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.inviteRepository = inviteRepository;
        this.grantAuthority = grantAuthority;
        this.ttlHours = ttlHours;
    }

    @Transactional
    public IssuedInvite resend(ResendInviteCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        InviteId inviteId = Objects.requireNonNull(command.inviteId(), "inviteId");
        UserId actor = Objects.requireNonNull(command.actor(), "actor");

        // Inexistente ou de outro tenant: 404, sem revelar a existência (F6).
        Invite previous = inviteRepository.findById(inviteId)
            .filter(invite -> invite.tenantId().equals(tenantId))
            .orElseThrow(InviteNotFoundException::new);

        if (previous.status() == InviteStatus.ACCEPTED) {
            throw new InvalidInviteException();
        }
        if (userRepository.existsByTenantIdAndEmail(tenantId, previous.email())) {
            throw new UserEmailAlreadyExistsException(previous.email());
        }
        if (previous.roleId() == null) {
            throw new InvalidInviteException();
        }

        Role role = roleRepository.findById(RoleId.of(previous.roleId()))
            .orElseThrow(InvalidInviteException::new);
        SystemRole target = role.systemRole().orElseThrow(InvalidInviteException::new);
        if (!grantAuthority.canGrantByInvite(tenantId, actor, target, previous.departmentId())) {
            throw new AuthorizationDeniedException(
                "Ator não pode conceder o papel do convite.");
        }

        // Revoga o convite anterior (idempotente quando já revogado).
        previous.revoke();
        inviteRepository.save(previous);

        String rawToken = InviteTokens.random();
        Instant expiresAt = Instant.now().plus(Duration.ofHours(ttlHours));
        Invite renewed = Invite.create(
            InviteId.random(),
            tenantId,
            previous.email(),
            previous.hierarchyLevel(),
            previous.departmentId(),
            role.id().value(),
            actor.value(),
            InviteTokenHasher.sha256Hex(rawToken),
            expiresAt
        );
        return new IssuedInvite(inviteRepository.save(renewed), rawToken);
    }
}
