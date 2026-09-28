package br.com.qsoft.governance.aigateway.infrastructure.web;

import java.util.Set;
import java.util.UUID;

/**
 * Payload de configuração da ModelPolicy do departamento (SPI-82).
 * Invariante (ADR-08): com permitidos, o default é obrigatório e pertence ao
 * conjunto; conjunto vazio implica default nulo.
 */
public record ModelPolicyRequest(
    Set<UUID> allowedModelIds,
    UUID defaultModelId
) {
}
