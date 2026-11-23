package br.com.qsoft.governance.agents.domain;

import static br.com.qsoft.governance.agents.domain.AgentInvariants.requireJson;
import static br.com.qsoft.governance.agents.domain.AgentInvariants.requireNonNull;
import static br.com.qsoft.governance.agents.domain.AgentInvariants.requirePositive;

import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot imutável do grafo de um fluxo (ADR-44). Versões publicadas são
 * pinadas na execução; o módulo não expõe operação de alteração.
 */
public record AgentFlowVersion(
    UUID id,
    UUID flowId,
    int versionNumber,
    int schemaVersion,
    String graphJson,
    AgentValidationStatus validationStatus,
    UserId createdBy,
    Instant createdAt
) {

    public AgentFlowVersion {
        requireNonNull(id, "id");
        requireNonNull(flowId, "flowId");
        requirePositive(versionNumber, "versionNumber");
        requirePositive(schemaVersion, "schemaVersion");
        requireJson(graphJson, "graphJson");
        requireNonNull(validationStatus, "validationStatus");
        requireNonNull(createdBy, "createdBy");
        requireNonNull(createdAt, "createdAt");
    }
}
