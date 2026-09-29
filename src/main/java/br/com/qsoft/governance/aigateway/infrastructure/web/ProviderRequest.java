package br.com.qsoft.governance.aigateway.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload de criação de Provider. */
public record ProviderRequest(
    @NotBlank(message = "name é obrigatório")
    @Size(max = 150, message = "name deve ter no máximo 150 caracteres")
    String name
) {
}
