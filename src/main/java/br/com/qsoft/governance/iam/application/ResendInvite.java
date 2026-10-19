package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.InviteRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.InvalidInviteException;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.iam.domain.invite.InviteStatus;
import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso de reenvio de convite (SPI-59): gera um NOVO token (guardado como
 * hash) e nova expiração a partir de um convite existente não aceito, revogando
 * o anterior — sem duplicar o registro de usuário. Os vínculos de concessão do
 * convite ({@code roleId}/{@code invitedBy}) são preservados.
 * <p>
 * A AUTORIZAÇÃO (INVITE:MANAGE) é aplicada no endpoint via
 * {@code @RequiresPermission}. O escopo de tenant é validado aqui (ADR-03):
 * convite de outro tenant é tratado como convite inválido, sem revelar existência.
 */
@Service
public class ResendInvite {

    private final UserRepository userRepository;
    private final InviteRepository inviteRepository;
    private final long ttlHours;

    public ResendInvite(UserRepository userRepository,
                        InviteRepository inviteRepository,
                        @Value("${app.security.invite.ttl-hours:48}") long ttlHours) {
        this.userRepository = userRepository;
        this.inviteRepository = inviteRepository;
        this.ttlHours = ttlHours;
    }

    /**
     * Revoga o convite anterior e grava o novo em uma única transação: sem isso,
     * uma falha no segundo {@code save} deixaria o convite revogado sem substituto.
     */
    @Transactional
    public IssuedInvite resend(ResendInviteCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        InviteId inviteId = Objects.requireNonNull(command.inviteId(), "inviteId");

        Invite previous = inviteRepository.findById(inviteId)
            .filter(invite -> invite.tenantId().equals(tenantId))
            .orElseThrow(InvalidInviteException::new);

        if (previous.status() == InviteStatus.ACCEPTED) {
            throw new InvalidInviteException();
        }
        if (userRepository.existsByTenantIdAndEmail(tenantId, previous.email())) {
            throw new UserEmailAlreadyExistsException(previous.email());
        }

        // Revoga o convite anterior (idempotente quando já revogado).
        previous.revoke();
        inviteRepository.save(previous);

        String rawToken = generateToken();
        Instant expiresAt = Instant.now().plus(Duration.ofHours(ttlHours));
        Invite renewed = Invite.create(
            InviteId.random(),
            tenantId,
            previous.email(),
            previous.hierarchyLevel(),
            previous.departmentId(),
            previous.roleId(),
            previous.invitedBy(),
            InviteTokenHasher.sha256Hex(rawToken),
            expiresAt
        );
        return new IssuedInvite(inviteRepository.save(renewed), rawToken);
    }

    private String generateToken() {
        String token;
        do {
            token = UUID.randomUUID().toString().replace("-", "");
        } while (inviteRepository.existsByTokenHash(InviteTokenHasher.sha256Hex(token)));
        return token;
    }
}
