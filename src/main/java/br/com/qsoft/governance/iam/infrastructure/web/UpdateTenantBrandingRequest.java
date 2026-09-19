package br.com.qsoft.governance.iam.infrastructure.web;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload de entrada para atualização de identidade visual do tenant (SPI-53).
 * Todos os campos são opcionais — envie apenas o que deseja alterar.
 */
public record UpdateTenantBrandingRequest(
    @Size(max = 500, message = "logoUrl deve ter no máximo 500 caracteres")
    String logoUrl,

    @Pattern(regexp = "^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$",
             message = "primaryColor deve ser um código hex válido (ex: #3B82F6)")
    String primaryColor,

    @Pattern(regexp = "^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$",
             message = "secondaryColor deve ser um código hex válido (ex: #1E40AF)")
    String secondaryColor
) {
}
