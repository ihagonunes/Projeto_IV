package br.com.qsoft.governance.aigateway.infrastructure.web;

import br.com.qsoft.governance.aigateway.application.ResolvedModel;
import java.util.UUID;

/** Representação do modelo resolvido pelo roteamento determinístico (SPI-84). */
public record ResolvedModelResponse(
    UUID modelId,
    String modelName,
    UUID providerId,
    String providerName
) {

    public static ResolvedModelResponse from(ResolvedModel resolved) {
        return new ResolvedModelResponse(
            resolved.modelId(),
            resolved.modelName(),
            resolved.providerId(),
            resolved.providerName()
        );
    }
}
