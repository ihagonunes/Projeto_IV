package br.com.qsoft.governance.aigateway.infrastructure.web;

import br.com.qsoft.governance.aigateway.domain.provider.ProviderKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload de criação de Provider (SPEC-03 §4). Nunca recebe o segredo: apenas
 * a referência ({@code credentialRef}) à variável de ambiente.
 */
public record ProviderRequest(
    @NotBlank(message = "name é obrigatório")
    @Size(max = 150, message = "name deve ter no máximo 150 caracteres")
    String name,

    ProviderKind kind,

    @Size(max = 500, message = "baseUrl deve ter no máximo 500 caracteres")
    String baseUrl,

    @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,99}$", message = "credentialRef inválido")
    String credentialRef,

    @Min(value = 1000, message = "timeoutMs deve ser no mínimo 1000")
    @Max(value = 120000, message = "timeoutMs deve ser no máximo 120000")
    Integer timeoutMs
) {
}
