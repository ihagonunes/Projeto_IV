package br.com.qsoft.governance.aigateway.infrastructure.web;

import br.com.qsoft.governance.aigateway.domain.model.ModelStatus;
import jakarta.validation.constraints.NotNull;

/** Payload de ativação/desativação de Model (SPI-81). */
public record ModelStatusRequest(
    @NotNull(message = "status é obrigatório")
    ModelStatus status
) {
}
