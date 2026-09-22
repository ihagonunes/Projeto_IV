package br.com.qsoft.governance.iam.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload de edição dos dados cadastrais do tenant atual (SPI-52).
 * O slug não é editável (identifica a organização na resolução do tenant).
 */
public record TenantUpdateRequest(
    @NotBlank(message = "name é obrigatório")
    @Size(max = 200, message = "name deve ter no máximo 200 caracteres")
    String name
) {
}
