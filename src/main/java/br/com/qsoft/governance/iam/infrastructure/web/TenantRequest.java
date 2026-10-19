package br.com.qsoft.governance.iam.infrastructure.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload de entrada para cadastro de tenant. Inclui os dados do primeiro
 * administrador (ADR-37 §7.1): o registrante vira {@code TENANT_ADMIN}.
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
    String emailDomain,

    @NotBlank(message = "adminName é obrigatório")
    @Size(max = 200, message = "adminName deve ter no máximo 200 caracteres")
    String adminName,

    @NotBlank(message = "adminEmail é obrigatório")
    @Email(message = "adminEmail inválido")
    @Size(max = 255, message = "adminEmail deve ter no máximo 255 caracteres")
    String adminEmail,

    @NotBlank(message = "adminPassword é obrigatória")
    @Size(min = 8, message = "adminPassword deve ter ao menos 8 caracteres")
    String adminPassword
) {
}
