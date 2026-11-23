package br.com.qsoft.governance.agents.infrastructure.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/**
 * Nova versão do fluxo. `rowVersion` é alternativa ao header `If-Match`; um dos
 * dois é obrigatório (senão 400) e divergência → 412.
 */
public record SaveAgentFlowVersionRequest(
    @Size(max = 120) String name,
    @NotNull @Min(1) Integer schemaVersion,
    @NotNull JsonNode graphJson,
    Long rowVersion
) {
}
