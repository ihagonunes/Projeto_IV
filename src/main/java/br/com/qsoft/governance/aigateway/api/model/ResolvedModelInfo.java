package br.com.qsoft.governance.aigateway.api.model;

import java.util.UUID;

/** Visão pública do modelo resolvido para um usuário (ADR-38). */
public record ResolvedModelInfo(
    UUID modelId,
    String modelName,
    UUID providerId,
    String providerName
) {
}
