package br.com.qsoft.governance.agents.infrastructure.web;

import br.com.qsoft.governance.agents.application.AgentCatalogEntry;
import br.com.qsoft.governance.agents.domain.AgentDefinitionStatus;
import java.time.Instant;

/** Item do catálogo efetivo; não expõe prompt nem políticas completas. */
public record AgentCatalogEntryResponse(
    String agentKey,
    int version,
    String name,
    String description,
    String category,
    AgentDefinitionStatus status,
    AgentDefinitionStatus globalStatus,
    boolean executable,
    boolean overridden,
    Instant overrideUpdatedAt
) {

    static AgentCatalogEntryResponse from(AgentCatalogEntry entry) {
        return new AgentCatalogEntryResponse(entry.agentKey(), entry.version(), entry.name(),
            entry.description(), entry.category(), entry.status(), entry.globalStatus(),
            entry.executable(), entry.overridden(), entry.overrideUpdatedAt());
    }
}
