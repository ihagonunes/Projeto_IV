package br.com.qsoft.governance.iam.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload de login (tenantSlug + email + password).
 */
public record AuthRequest(
    @NotBlank(message = "tenantSlug é obrigatório")
    String tenantSlug,

    @NotBlank(message = "email é obrigatório")
    String email,

    @NotBlank(message = "password é obrigatória")
    String password
) {
}
