package br.com.qsoft.governance.agents.infrastructure.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

/** Validação de grafo sem persistir. */
public record ValidateAgentFlowRequest(
    @NotNull @Min(1) Integer schemaVersion,
    @NotNull JsonNode graphJson
) {
}
