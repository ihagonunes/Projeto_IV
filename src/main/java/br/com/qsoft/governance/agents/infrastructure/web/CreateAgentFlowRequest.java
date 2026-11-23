package br.com.qsoft.governance.agents.infrastructure.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * Criação de fluxo. `id`, `tenantId`, `ownerUserId` e `versionNumber` não têm
 * campo: identidade e dono vêm do contexto autenticado.
 */
public record CreateAgentFlowRequest(
    @NotBlank @Size(max = 120) String name,
    UUID departmentId,
    @NotNull @Min(1) Integer schemaVersion,
    @NotNull JsonNode graphJson
) {
}
