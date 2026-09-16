package br.com.qsoft.governance.iam.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload de aceite do convite (token + definição de senha).
 */
public record AcceptInviteRequest(
    @NotBlank(message = "token é obrigatório")
    String token,

    String name,

    @NotBlank(message = "password é obrigatória")
    String password
) {
}
