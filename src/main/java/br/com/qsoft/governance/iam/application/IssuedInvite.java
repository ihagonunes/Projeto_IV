package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.domain.invite.Invite;
import java.util.Objects;

/**
 * Resultado da emissão/reenvio de convite: o convite persistido (que guarda
 * apenas o hash do token) e o token puro, devolvido <b>uma única vez</b> a quem
 * convida (ADR-37). O token nunca vai para log.
 */
public record IssuedInvite(Invite invite, String token) {

    public IssuedInvite {
        Objects.requireNonNull(invite, "invite é obrigatório.");
        Objects.requireNonNull(token, "token é obrigatório.");
    }
}
