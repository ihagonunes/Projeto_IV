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

/**
 * Caso de uso de reenvio de convite (SPI-59): gera um NOVO convite (token e
 * expiração novos) a partir de um convite existente não aceito, revogando o
 * anterior — sem duplicar o registro de usuário.
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

    public Invite resend(ResendInviteCommand command) {
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

        Instant expiresAt = Instant.now().plus(Duration.ofHours(ttlHours));
        Invite renewed = Invite.create(
            InviteId.random(),
            tenantId,
            previous.email(),
            previous.hierarchyLevel(),
            previous.departmentId(),
            generateToken(),
            expiresAt
        );
        return inviteRepository.save(renewed);
    }

    private String generateToken() {
        String token;
        do {
            token = UUID.randomUUID().toString().replace("-", "");
        } while (inviteRepository.existsByToken(token));
        return token;
    }
}
