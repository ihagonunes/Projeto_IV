package br.com.qsoft.governance.iam.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload de login (email + password; tenantSlug opcional).
 * Se tenantSlug não for informado, o backend deriva o tenant a partir do
 * domínio do e-mail (fallback) — ver AuthenticateUser.
 */
public record AuthRequest(
    @Size(max = 100, message = "tenantSlug deve ter no máximo 100 caracteres")
    String tenantSlug,

    @NotBlank(message = "email é obrigatório")
    String email,

    @NotBlank(message = "password é obrigatória")
    String password
) {
}
