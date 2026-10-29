package br.com.qsoft.governance.aigateway.api.model;

import java.util.UUID;

/**
 * Modelo visível no seletor do chat (ADR-42): política do departamento +
 * disponibilidade atual do catálogo.
 */
public record AllowedModel(
    UUID id,
    String name,
    String providerName,
    boolean available,
    String unavailableReason
) {
}
