package br.com.qsoft.governance.agents.infrastructure.web;

import br.com.qsoft.governance.agents.application.AgentCatalogEntry;
import java.util.List;

/** Catálogo efetivo do tenant, na ordem estável de categoria/nome. */
public record AgentCatalogResponse(List<AgentCatalogEntryResponse> agentes) {

    static AgentCatalogResponse from(List<AgentCatalogEntry> entries) {
        return new AgentCatalogResponse(entries.stream()
            .map(AgentCatalogEntryResponse::from)
            .toList());
    }
}
