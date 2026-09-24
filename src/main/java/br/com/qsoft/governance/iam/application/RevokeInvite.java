package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.InviteRepository;
import br.com.qsoft.governance.iam.domain.exception.InvalidInviteException;
import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.iam.domain.invite.InviteStatus;
import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso de revogação de convite.
 * <p>
 * A AUTORIZAÇÃO (INVITE:MANAGE) é aplicada no endpoint via
 * {@code @RequiresPermission}. O escopo de tenant é validado aqui (ADR-03):
 * convite de outro tenant é tratado como convite inválido, sem revelar existência.
 */
@Service
public class RevokeInvite {

    private final InviteRepository inviteRepository;

    public RevokeInvite(InviteRepository inviteRepository) {
        this.inviteRepository = inviteRepository;
    }

    @Transactional
    public void revoke(RevokeInviteCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        InviteId inviteId = Objects.requireNonNull(command.inviteId(), "inviteId");

        Invite invite = inviteRepository.findById(inviteId)
            .filter(candidate -> candidate.tenantId().equals(tenantId))
            .orElseThrow(InvalidInviteException::new);

        if (invite.status() == InviteStatus.ACCEPTED) {
            throw new InvalidInviteException();
        }
        invite.revoke();
        inviteRepository.save(invite);
    }
}
