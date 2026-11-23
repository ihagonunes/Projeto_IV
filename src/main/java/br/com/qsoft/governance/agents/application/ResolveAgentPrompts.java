package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.agents.application.port.AgentCatalogOverrideRepository;
import br.com.qsoft.governance.agents.application.port.AgentCatalogRepository;
import br.com.qsoft.governance.agents.domain.AgentDefinition;
import br.com.qsoft.governance.agents.domain.AgentDefinitionOverride;
import br.com.qsoft.governance.agents.domain.FlowGraph;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Resolve o prompt efetivo de cada nó `agent` (global + override do tenant).
 * Sem RAG: `knowledge_scope`/`tools` ficam fora do MVP; só o prompt é usado.
 */
@Service
public class ResolveAgentPrompts {

    private final AgentCatalogRepository catalogRepository;
    private final AgentCatalogOverrideRepository overrideRepository;

    public ResolveAgentPrompts(AgentCatalogRepository catalogRepository,
                               AgentCatalogOverrideRepository overrideRepository) {
        this.catalogRepository = catalogRepository;
        this.overrideRepository = overrideRepository;
    }

    public Map<String, String> resolve(TenantId tenantId, FlowGraph graph) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
        Map<String, String> prompts = new LinkedHashMap<>();
        for (var node : graph.nodes()) {
            if (!"agent".equals(node.kind()) || node.agentKey() == null) {
                continue;
            }
            if (prompts.containsKey(node.agentKey())) {
                continue;
            }
            AgentDefinition definition = catalogRepository.findLatestByAgentKey(node.agentKey())
                .orElseThrow(AgentFlowNotValidatedException::new);
            AgentDefinitionOverride override = overrideRepository
                .find(tenantId, node.agentKey(), definition.version())
                .orElse(null);
            String prompt = override != null && override.promptTemplate() != null
                ? override.promptTemplate()
                : definition.promptTemplate();
            prompts.put(node.agentKey(), prompt);
        }
        return prompts;
    }
}
