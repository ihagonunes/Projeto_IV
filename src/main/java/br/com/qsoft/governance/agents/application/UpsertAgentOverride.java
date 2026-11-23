package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.agents.application.port.AgentCatalogOverrideRepository;
import br.com.qsoft.governance.agents.application.port.AgentCatalogRepository;
import br.com.qsoft.governance.agents.domain.AgentDefinition;
import br.com.qsoft.governance.agents.domain.AgentDefinitionOverride;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria ou atualiza o override do tenant para uma versão do catálogo global
 * (ADR-44). O {@code tenantId} vem do contexto autenticado; o catálogo global
 * nunca é alterado por aqui.
 */
@Service
public class UpsertAgentOverride {

    private final AgentCatalogRepository catalogRepository;
    private final AgentCatalogOverrideRepository overrideRepository;
    private final AgentPolicyJson policyJson;

    public UpsertAgentOverride(AgentCatalogRepository catalogRepository,
                               AgentCatalogOverrideRepository overrideRepository,
                               AgentPolicyJson policyJson) {
        this.catalogRepository = catalogRepository;
        this.overrideRepository = overrideRepository;
        this.policyJson = policyJson;
    }

    @Transactional
    public AgentCatalogEntry upsert(AgentOverrideCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        String agentKey = requireAgentKey(command.agentKey());
        if (command.version() < 1) {
            throw new IllegalArgumentException("version deve ser maior que zero.");
        }
        requireAtLeastOneField(command);
        String prompt = normalizedOptional(command.promptTemplate(), "promptTemplate");
        String tools = normalizedOptional(command.toolsJson(), "toolsJson");
        String knowledge = normalizedOptional(command.knowledgeScopeJson(), "knowledgeScopeJson");
        String modelPolicy = normalizedOptional(command.modelPolicyJson(), "modelPolicyJson");
        validatePolicies(tools, knowledge, modelPolicy);

        AgentDefinition definition = catalogRepository
            .findByAgentKey(agentKey, command.version())
            .orElseThrow(() -> new AgentCatalogEntryNotFoundException(agentKey, command.version()));

        AgentDefinitionOverride current = overrideRepository
            .find(tenantId, agentKey, command.version())
            .orElse(null);
        if (command.expectedUpdatedAt() != null
            && (current == null || !sameInstant(current.updatedAt(), command.expectedUpdatedAt()))) {
            throw new AgentOverrideConflictException();
        }

        AgentDefinitionOverride saved = new AgentDefinitionOverride(tenantId, agentKey,
            command.version(), prompt, tools, knowledge, modelPolicy, command.status(),
            Instant.now());
        overrideRepository.save(saved);
        return AgentCatalogEntry.resolve(definition, saved);
    }

    private static String requireAgentKey(String agentKey) {
        if (agentKey == null || agentKey.isBlank()) {
            throw new IllegalArgumentException("agentKey é obrigatório.");
        }
        return agentKey.trim();
    }

    private static void requireAtLeastOneField(AgentOverrideCommand command) {
        if (command.promptTemplate() == null && command.toolsJson() == null
            && command.knowledgeScopeJson() == null && command.modelPolicyJson() == null
            && command.status() == null) {
            throw new IllegalArgumentException(
                "Informe ao menos um campo do override (prompt, ferramentas, "
                    + "conhecimento, política ou status).");
        }
    }

    private static String normalizedOptional(String value, String field) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " não pode ser vazio.");
        }
        return value;
    }

    private void validatePolicies(String tools, String knowledge, String modelPolicy) {
        if (tools != null) {
            policyJson.validateTools(tools);
        }
        if (knowledge != null) {
            policyJson.validateKnowledgeScope(knowledge);
        }
        if (modelPolicy != null) {
            policyJson.validateModelPolicy(modelPolicy);
        }
    }

    /**
     * Comparação em milissegundos: o PostgreSQL guarda microssegundos, mas
     * clientes JavaScript serializam até milissegundos. Evita 409 falso sem
     * perder a detecção de concorrência real.
     */
    private static boolean sameInstant(Instant left, Instant right) {
        return left.truncatedTo(ChronoUnit.MILLIS)
            .equals(right.truncatedTo(ChronoUnit.MILLIS));
    }
}
