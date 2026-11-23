package br.com.qsoft.governance.agents.infrastructure.web;

import br.com.qsoft.governance.agents.domain.AgentFlowVersion;
import br.com.qsoft.governance.agents.domain.AgentValidationStatus;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Versão imutável do grafo exposta pela API (JSON já parseado). */
public record AgentFlowVersionResponse(
    UUID id,
    int versionNumber,
    int schemaVersion,
    AgentValidationStatus validationStatus,
    JsonNode graph,
    UUID createdBy,
    Instant createdAt
) {

    static AgentFlowVersionResponse from(AgentFlowVersion version, JsonNode graph) {
        return new AgentFlowVersionResponse(version.id(), version.versionNumber(),
            version.schemaVersion(), version.validationStatus(), graph, version.createdBy().value(),
            version.createdAt());
    }
}
