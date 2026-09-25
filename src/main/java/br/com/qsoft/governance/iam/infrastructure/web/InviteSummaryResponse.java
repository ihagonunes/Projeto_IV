package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.invite.Invite;
import java.time.Instant;
import java.util.UUID;

/**
 * Representação pública de um convite em listagens — <b>sem o token</b>.
 * O token (credencial de aceite) é devolvido apenas na emissão/reenvio, para o
 * administrador encaminhar; listar não deve expô-lo.
 */
public record InviteSummaryResponse(
    UUID id,
    String email,
    String hierarchyLevel,
    UUID departmentId,
    String status,
    Instant createdAt,
    Instant expiresAt
) {

    public static InviteSummaryResponse from(Invite invite) {
        return new InviteSummaryResponse(
            invite.id().value(),
            invite.email(),
            invite.hierarchyLevel().name(),
            invite.departmentId(),
            invite.status().name(),
            invite.createdAt(),
            invite.expiresAt()
        );
    }
}
