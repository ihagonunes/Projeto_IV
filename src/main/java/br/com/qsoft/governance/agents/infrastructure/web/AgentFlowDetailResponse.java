package br.com.qsoft.governance.agents.infrastructure.web;

import br.com.qsoft.governance.agents.application.AgentFlowDetail;
import br.com.qsoft.governance.agents.domain.AgentFlowStatus;
import java.time.Instant;
import java.util.UUID;

/** Fluxo com a versão corrente e o grafo. */
public record AgentFlowDetailResponse(
    UUID id,
    String name,
    AgentFlowStatus status,
    UUID ownerUserId,
    UUID departmentId,
    long rowVersion,
    UUID currentVersionId,
    AgentFlowVersionResponse currentVersion,
    Instant createdAt,
    Instant updatedAt
) {

    static AgentFlowDetailResponse from(AgentFlowDetail detail,
                                        AgentFlowVersionResponse currentVersion) {
        var flow = detail.flow();
        return new AgentFlowDetailResponse(flow.id(), flow.name(), flow.status(),
            flow.ownerUserId().value(), flow.departmentId(), flow.rowVersion(),
            flow.currentVersionId(), currentVersion, flow.createdAt(), flow.updatedAt());
    }
}
