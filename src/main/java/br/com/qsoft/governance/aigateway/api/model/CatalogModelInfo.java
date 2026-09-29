package br.com.qsoft.governance.aigateway.api.model;

import java.util.UUID;

/** Visão pública de um Model do catálogo (contrato do módulo aigateway). */
public record CatalogModelInfo(
    UUID id,
    UUID providerId,
    String name,
    String status
) {
}
