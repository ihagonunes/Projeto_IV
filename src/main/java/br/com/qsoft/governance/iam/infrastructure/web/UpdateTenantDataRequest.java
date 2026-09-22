package br.com.qsoft.governance.iam.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload de entrada para atualização de dados cadastrais do tenant (SPI-52).
 */
public record UpdateTenantDataRequest(
    @NotBlank(message = "name é obrigatório")
    @Size(max = 200, message = "name deve ter no máximo 200 caracteres")
    String name
) {
}
