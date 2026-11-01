package br.com.qsoft.governance.aigateway.infrastructure.web;

import br.com.qsoft.governance.aigateway.domain.model.Model;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Representação pública de um Model do catálogo. */
public record ModelResponse(
    UUID id,
    UUID providerId,
    String name,
    String externalName,
    String status,
    BigDecimal inputPricePer1k,
    BigDecimal outputPricePer1k,
    Instant createdAt
) {

    public static ModelResponse from(Model model) {
        return new ModelResponse(
            model.id(),
            model.providerId(),
            model.name(),
            model.externalName(),
            model.status().name(),
            model.inputPricePer1k(),
            model.outputPricePer1k(),
            model.createdAt()
        );
    }
}
