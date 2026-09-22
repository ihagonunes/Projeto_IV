package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.user.UserStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Payload de desativação/reativação de usuário (SPI-61).
 */
public record UserStatusRequest(
    @NotNull(message = "status é obrigatório")
    UserStatus status
) {
}
