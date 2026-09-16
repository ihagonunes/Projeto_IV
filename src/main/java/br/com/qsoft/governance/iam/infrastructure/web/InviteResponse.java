package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.invite.Invite;
import java.time.Instant;
import java.util.UUID;

/**
 * Representação pública de um convite (inclui o token/link a ser enviado).
 */
public record InviteResponse(
    UUID id,
    String email,
    String hierarchyLevel,
    String token,
    String status,
    Instant expiresAt
) {

    public static InviteResponse from(Invite invite) {
        return new InviteResponse(
            invite.id().value(),
            invite.email(),
            invite.hierarchyLevel().name(),
            invite.token(),
            invite.status().name(),
            invite.expiresAt()
        );
    }
}
