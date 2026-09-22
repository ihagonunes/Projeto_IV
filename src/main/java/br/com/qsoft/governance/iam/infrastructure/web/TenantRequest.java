package br.com.qsoft.governance.iam.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload de entrada para cadastro de tenant.
 */
public record TenantRequest(
    @NotBlank(message = "slug é obrigatório")
    @Size(max = 100, message = "slug deve ter no máximo 100 caracteres")
    String slug,

    @NotBlank(message = "name é obrigatório")
    @Size(max = 200, message = "name deve ter no máximo 200 caracteres")
    String name,

    @NotBlank(message = "emailDomain é obrigatório")
    @Size(max = 253, message = "emailDomain deve ter no máximo 253 caracteres")
    @Pattern(
        regexp = "^@?[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)+$",
        message = "emailDomain inválido")
    String emailDomain
) {
}
