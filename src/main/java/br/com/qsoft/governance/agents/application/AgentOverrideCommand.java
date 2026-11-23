package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.agents.domain.AgentDefinitionStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;

/**
 * Pedido de override do catálogo por tenant (ADR-44). Campos nulos herdam o
 * valor global; {@code expectedUpdatedAt} é opcional e habilita concorrência
 * otimista (divergência → 409).
 */
public record AgentOverrideCommand(
    TenantId tenantId,
    String agentKey,
    int version,
    String promptTemplate,
    String toolsJson,
    String knowledgeScopeJson,
    String modelPolicyJson,
    AgentDefinitionStatus status,
    Instant expectedUpdatedAt
) {
}
