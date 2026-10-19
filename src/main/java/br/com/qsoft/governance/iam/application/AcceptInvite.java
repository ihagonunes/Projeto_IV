package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.InviteRepository;
import br.com.qsoft.governance.iam.domain.exception.InvalidInviteException;
import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.iam.domain.user.User;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso de aceite do convite (SPI-30): valida o token (pendente e não
 * expirado), cria o usuário no tenant/papel do convite com a senha (BCrypt) e
 * invalida o token (rejeita reutilização). Atômico.
 */
@Service
public class AcceptInvite {

    private final InviteRepository inviteRepository;
    private final ProvisionUser provisionUser;

    public AcceptInvite(InviteRepository inviteRepository, ProvisionUser provisionUser) {
        this.inviteRepository = inviteRepository;
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

        User user = provisionUser.provision(new ProvisionUserCommand(
            invite.tenantId(),
            invite.email(),
            name != null ? name : invite.email(),
            invite.hierarchyLevel(),
            password,
            invite.departmentId(),
            Set.of()
        ));

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
