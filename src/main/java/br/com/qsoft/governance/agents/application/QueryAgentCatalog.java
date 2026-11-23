package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.agents.application.port.AgentCatalogOverrideRepository;
import br.com.qsoft.governance.agents.application.port.AgentCatalogRepository;
import br.com.qsoft.governance.agents.domain.AgentDefinition;
import br.com.qsoft.governance.agents.domain.AgentDefinitionOverride;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lista o catálogo global (versão mais recente por {@code agent_key}) já
 * resolvido com o override do tenant autenticado (ADR-44).
 */
@Service
public class QueryAgentCatalog {

    private static final Comparator<AgentCatalogEntry> ORDER =
        Comparator.comparing(AgentCatalogEntry::category)
            .thenComparing(AgentCatalogEntry::name)
            .thenComparing(AgentCatalogEntry::agentKey);

    private final AgentCatalogRepository catalogRepository;
    private final AgentCatalogOverrideRepository overrideRepository;

    public QueryAgentCatalog(AgentCatalogRepository catalogRepository,
                             AgentCatalogOverrideRepository overrideRepository) {
        this.catalogRepository = catalogRepository;
        this.overrideRepository = overrideRepository;
    }

    @Transactional(readOnly = true)
    public List<AgentCatalogEntry> query(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");

        Map<String, AgentDefinition> latest = new LinkedHashMap<>();
        for (AgentDefinition definition : catalogRepository.findAll()) {
            latest.merge(definition.agentKey(), definition,
                (current, candidate) -> candidate.version() > current.version()
                    ? candidate : current);
        }

        List<AgentDefinitionOverride> overrides = overrideRepository.findByTenant(tenantId);
        return latest.values().stream()
            .map(definition -> AgentCatalogEntry.resolve(definition,
                overrideFor(overrides, definition)))
            .sorted(ORDER)
            .toList();
    }

    private static AgentDefinitionOverride overrideFor(List<AgentDefinitionOverride> overrides,
                                                        AgentDefinition definition) {
        return overrides.stream()
            .filter(override -> override.agentKey().equals(definition.agentKey())
                && override.version() == definition.version())
            .findFirst()
            .orElse(null);
    }
}
