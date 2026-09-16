package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.user.User;
import java.util.UUID;

/**
 * Resposta do aceite do convite.
 */
public record AcceptInviteResponse(UUID userId, String email, String status) {

    public static AcceptInviteResponse from(User user) {
        return new AcceptInviteResponse(
            user.id().value(),
            user.email(),
            user.status().name()
        );
    }
}
