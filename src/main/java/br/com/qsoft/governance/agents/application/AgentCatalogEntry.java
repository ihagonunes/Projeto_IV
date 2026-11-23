package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.agents.domain.AgentDefinition;
import br.com.qsoft.governance.agents.domain.AgentDefinitionOverride;
import br.com.qsoft.governance.agents.domain.AgentDefinitionStatus;
import java.time.Instant;

/**
 * Definição efetiva de um agente para um tenant: catálogo global resolvido com
 * o override do tenant (ADR-44). Não carrega prompt nem políticas completas.
 */
public record AgentCatalogEntry(
    String agentKey,
    int version,
    String name,
    String description,
    String category,
    AgentDefinitionStatus globalStatus,
    AgentDefinitionStatus status,
    boolean executable,
    boolean overridden,
    Instant overrideUpdatedAt
) {

    public AgentCatalogEntry {
        if (agentKey == null || agentKey.isBlank()) {
            throw new IllegalArgumentException("agentKey é obrigatório.");
        }
        if (globalStatus == null || status == null) {
            throw new IllegalArgumentException("status é obrigatório.");
        }
    }

    /** Resolve `global` + `override`. Status efetivo nulo só no global inválido. */
    public static AgentCatalogEntry resolve(AgentDefinition definition,
                                            AgentDefinitionOverride override) {
        AgentDefinitionStatus effective = override != null && override.status() != null
            ? override.status()
            : definition.status();
        return new AgentCatalogEntry(
            definition.agentKey(),
            definition.version(),
            definition.name(),
            definition.description(),
            definition.category(),
            definition.status(),
            effective,
            effective == AgentDefinitionStatus.ACTIVE,
            override != null,
            override == null ? null : override.updatedAt());
    }
}
