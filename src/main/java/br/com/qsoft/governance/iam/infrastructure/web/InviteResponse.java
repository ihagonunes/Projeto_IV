package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.application.IssuedInvite;
import br.com.qsoft.governance.iam.domain.invite.Invite;
import java.time.Instant;
import java.util.UUID;

/**
 * Representação pública de um convite (inclui o token/link a ser enviado uma
 * única vez a quem convida).
 */
public record InviteResponse(
    UUID id,
    String email,
    String hierarchyLevel,
    String token,
    String status,
    Instant expiresAt
) {

    public static InviteResponse from(IssuedInvite issued) {
        Invite invite = issued.invite();
        return new InviteResponse(
            invite.id().value(),
            invite.email(),
            invite.hierarchyLevel().name(),
            issued.token(),
            invite.status().name(),
            invite.expiresAt()
        );
    }
}
