package br.com.qsoft.governance.agents.domain;

import static br.com.qsoft.governance.agents.domain.AgentInvariants.requireJson;
import static br.com.qsoft.governance.agents.domain.AgentInvariants.requireNonNull;
import static br.com.qsoft.governance.agents.domain.AgentInvariants.requirePositive;
import static br.com.qsoft.governance.agents.domain.AgentInvariants.requireText;

import java.time.Instant;
import java.util.UUID;

/**
 * Definição <b>global</b> de agente (ADR-44), identificada por
 * {@code agent_key} estável + {@code version}. Não tem {@code tenant_id}: o
 * tenant só grava {@link AgentDefinitionOverride}. Os campos de política
 * (ferramentas, conhecimento e modelo) são JSON canônico em texto; o domínio
 * não depende de biblioteca de serialização.
 */
public record AgentDefinition(
    UUID id,
    String agentKey,
    int version,
    String name,
    String description,
    String category,
    String promptTemplate,
    String toolsJson,
    String knowledgeScopeJson,
    String modelPolicyJson,
    AgentDefinitionStatus status,
    Instant createdAt,
    Instant updatedAt
) {

    public AgentDefinition {
        requireNonNull(id, "id");
        agentKey = requireText(agentKey, "agentKey", 80);
        version = requirePositive(version, "version");
        name = requireText(name, "name", 120);
        requireNonNull(description, "description");
        category = requireText(category, "category", 60);
        promptTemplate = requireText(promptTemplate, "promptTemplate");
        toolsJson = requireJson(toolsJson, "toolsJson");
        knowledgeScopeJson = requireJson(knowledgeScopeJson, "knowledgeScopeJson");
        modelPolicyJson = requireJson(modelPolicyJson, "modelPolicyJson");
        requireNonNull(status, "status");
        requireNonNull(createdAt, "createdAt");
        requireNonNull(updatedAt, "updatedAt");
    }

    /** Primeira versão de uma definição ainda em rascunho, com políticas vazias. */
    public static AgentDefinition create(UUID id, String agentKey, int version, String name,
                                         String description, String category,
                                         String promptTemplate, Instant now) {
        requireNonNull(now, "now");
        return new AgentDefinition(id, agentKey, version, name, description, category,
            promptTemplate, "[]", "{}", "{}", AgentDefinitionStatus.DRAFT, now, now);
    }
}
