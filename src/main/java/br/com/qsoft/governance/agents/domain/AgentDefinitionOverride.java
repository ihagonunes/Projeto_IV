package br.com.qsoft.governance.agents.domain;

import static br.com.qsoft.governance.agents.domain.AgentInvariants.requireNonNull;
import static br.com.qsoft.governance.agents.domain.AgentInvariants.requirePositive;
import static br.com.qsoft.governance.agents.domain.AgentInvariants.requireText;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;

/**
 * Override <b>tenant-scoped</b> de uma definição global (ADR-44). Campo nulo
 * herda o valor global; {@code status} nulo herda o status global.
 */
public record AgentDefinitionOverride(
    TenantId tenantId,
    String agentKey,
    int version,
    String promptTemplate,
    String toolsJson,
    String knowledgeScopeJson,
    String modelPolicyJson,
    AgentDefinitionStatus status,
    Instant updatedAt
) {

    public AgentDefinitionOverride {
        requireNonNull(tenantId, "tenantId");
        agentKey = requireText(agentKey, "agentKey", 80);
        version = requirePositive(version, "version");
        requireNonNull(updatedAt, "updatedAt");
    }

    /** Override vazio: mantém o catálogo global intacto para este tenant. */
    public static AgentDefinitionOverride inheriting(TenantId tenantId, String agentKey, int version,
                                                     Instant updatedAt) {
        return new AgentDefinitionOverride(tenantId, agentKey, version,
            null, null, null, null, null, updatedAt);
    }
}
