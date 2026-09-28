package br.com.qsoft.governance.aigateway.application;

import java.util.UUID;

/** Modelo resolvido pelo roteamento determinístico (SPI-83/84). */
public record ResolvedModel(
    UUID modelId,
    String modelName,
    UUID providerId,
    String providerName
) {
}
