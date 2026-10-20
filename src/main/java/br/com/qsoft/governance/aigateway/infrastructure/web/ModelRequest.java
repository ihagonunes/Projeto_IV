package br.com.qsoft.governance.aigateway.infrastructure.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Payload de criação de Model (referencia o Provider por ID — ADR-09). */
public record ModelRequest(
    @NotNull(message = "providerId é obrigatório")
    UUID providerId,

    @NotBlank(message = "name é obrigatório")
    @Size(max = 150, message = "name deve ter no máximo 150 caracteres")
    String name,

    @Size(max = 150, message = "externalName deve ter no máximo 150 caracteres")
    String externalName,

    @DecimalMin(value = "0.00", message = "inputPricePer1k não pode ser negativo")
    BigDecimal inputPricePer1k,

    @DecimalMin(value = "0.00", message = "outputPricePer1k não pode ser negativo")
    BigDecimal outputPricePer1k
) {
}
