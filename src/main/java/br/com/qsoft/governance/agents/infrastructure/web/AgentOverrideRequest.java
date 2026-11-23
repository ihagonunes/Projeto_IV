package br.com.qsoft.governance.agents.infrastructure.web;

import br.com.qsoft.governance.agents.application.AgentOverrideCommand;
import br.com.qsoft.governance.agents.domain.AgentDefinitionStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Corpo do override do catálogo. Campos omitidos herdam o global;
 * {@code expectedUpdatedAt} ativa a checagem de concorrência otimista.
 * <p>
 * Vocabulário fechado no MVP: {@code toolsJson} é um array de textos,
 * {@code knowledgeScopeJson} aceita somente {@code {"mode":"NONE"}} e
 * {@code modelPolicyJson} somente {@code {"mode":"INHERIT"}} (sem RAG).
 */
public record AgentOverrideRequest(
    @NotNull @Min(1) Integer version,
    @Size(max = 20_000) String promptTemplate,
    @Size(max = 64_000) String toolsJson,
    @Size(max = 64_000) String knowledgeScopeJson,
    @Size(max = 64_000) String modelPolicyJson,
    AgentDefinitionStatus status,
    Instant expectedUpdatedAt
) {

    AgentOverrideCommand toCommand(TenantId tenantId, String agentKey) {
        return new AgentOverrideCommand(tenantId, agentKey, version, promptTemplate, toolsJson,
            knowledgeScopeJson, modelPolicyJson, status, expectedUpdatedAt);
    }
}
