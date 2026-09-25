package br.com.qsoft.governance.iam.infrastructure.web;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload de edição da identidade visual (white-label) do tenant (SPI-53).
 * Campos ausentes/vazios limpam o valor correspondente.
 */
public record TenantBrandingRequest(
    @Size(max = 500, message = "logoUrl deve ter no máximo 500 caracteres")
    String logoUrl,

    @Pattern(regexp = "^\\s*$|^#[0-9A-Fa-f]{6}$", message = "primaryColor deve estar no formato #RRGGBB")
    String primaryColor,

    @Pattern(regexp = "^\\s*$|^#[0-9A-Fa-f]{6}$", message = "secondaryColor deve estar no formato #RRGGBB")
    String secondaryColor
) {
}
