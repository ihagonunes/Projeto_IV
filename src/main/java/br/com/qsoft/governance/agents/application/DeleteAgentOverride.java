package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.agents.application.port.AgentCatalogOverrideRepository;
import br.com.qsoft.governance.agents.application.port.AgentCatalogRepository;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remove o override do tenant para um {@code agent_key}, voltando ao global
 * (ADR-44). Todas as versões do override daquele agente são removidas; o
 * catálogo global permanece intacto.
 */
@Service
public class DeleteAgentOverride {

    private final AgentCatalogRepository catalogRepository;
    private final AgentCatalogOverrideRepository overrideRepository;

    public DeleteAgentOverride(AgentCatalogRepository catalogRepository,
                               AgentCatalogOverrideRepository overrideRepository) {
        this.catalogRepository = catalogRepository;
        this.overrideRepository = overrideRepository;
    }

    @Transactional
    public int delete(TenantId tenantId, String agentKey) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
        if (agentKey == null || agentKey.isBlank()) {
            throw new IllegalArgumentException("agentKey é obrigatório.");
        }
        String normalized = agentKey.trim();
        if (!catalogRepository.existsByAgentKey(normalized)) {
            throw new AgentCatalogEntryNotFoundException(normalized);
        }
        return overrideRepository.deleteByTenantAndAgentKey(tenantId, normalized);
    }
}
