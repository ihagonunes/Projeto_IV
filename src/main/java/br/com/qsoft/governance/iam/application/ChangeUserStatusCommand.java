package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.UserId;

/**
 * Dados de entrada da desativação/reativação de usuário (SPI-61).
 * <code>actorUserId</code> identifica quem está realizando a ação.
 */
public record ChangeUserStatusCommand(
    UserId actorUserId,
    UserId targetUserId,
    UserStatus status
) {
}
