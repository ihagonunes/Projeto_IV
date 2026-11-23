package br.com.qsoft.governance.agents.infrastructure.web;

import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.agents.domain.AgentFlowStatus;
import java.time.Instant;
import java.util.UUID;

/** Item de listagem; não carrega o grafo. */
public record AgentFlowSummaryResponse(
    UUID id,
    String name,
    AgentFlowStatus status,
    UUID ownerUserId,
    UUID departmentId,
    long rowVersion,
    UUID currentVersionId,
    Instant updatedAt
) {

    static AgentFlowSummaryResponse from(AgentFlow flow) {
        return new AgentFlowSummaryResponse(flow.id(), flow.name(), flow.status(),
            flow.ownerUserId().value(), flow.departmentId(), flow.rowVersion(),
            flow.currentVersionId(), flow.updatedAt());
    }
}
