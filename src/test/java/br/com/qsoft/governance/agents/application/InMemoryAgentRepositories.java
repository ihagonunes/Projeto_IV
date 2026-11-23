package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.agents.application.port.AgentCatalogOverrideRepository;
import br.com.qsoft.governance.agents.application.port.AgentCatalogRepository;
import br.com.qsoft.governance.agents.domain.AgentDefinition;
import br.com.qsoft.governance.agents.domain.AgentDefinitionOverride;
import br.com.qsoft.governance.agents.domain.AgentDefinitionStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repositórios em memória para testes unitários do catálogo. */
final class InMemoryAgentCatalog implements AgentCatalogRepository {

    private final List<AgentDefinition> definitions = new ArrayList<>();

    InMemoryAgentCatalog(AgentDefinition... entries) {
        definitions.addAll(List.of(entries));
    }

    @Override
    public List<AgentDefinition> findAll() {
        return List.copyOf(definitions);
    }

    @Override
    public Optional<AgentDefinition> findByAgentKey(String agentKey, int version) {
        return definitions.stream()
            .filter(definition -> definition.agentKey().equals(agentKey)
                && definition.version() == version)
            .findFirst();
    }

    @Override
    public boolean existsByAgentKey(String agentKey) {
        return definitions.stream().anyMatch(definition -> definition.agentKey().equals(agentKey));
    }

    @Override
    public Optional<AgentDefinition> findLatestByAgentKey(String agentKey) {
        return definitions.stream()
            .filter(definition -> definition.agentKey().equals(agentKey))
            .max(java.util.Comparator.comparingInt(AgentDefinition::version));
    }

    static AgentDefinition definition(String agentKey, int version, AgentDefinitionStatus status) {
        Instant now = Instant.parse("2026-11-21T10:00:00Z");
        return new AgentDefinition(UUID.randomUUID(), agentKey, version, "Nome " + agentKey,
            "Descrição", "Gerais", "Prompt " + agentKey, "[]", "{}", "{}", status, now, now);
    }
}

final class InMemoryAgentOverrides implements AgentCatalogOverrideRepository {

    private final List<AgentDefinitionOverride> overrides = new ArrayList<>();

    @Override
    public List<AgentDefinitionOverride> findByTenant(TenantId tenantId) {
        return overrides.stream()
            .filter(override -> override.tenantId().equals(tenantId))
            .toList();
    }

    @Override
    public Optional<AgentDefinitionOverride> find(TenantId tenantId, String agentKey, int version) {
        return overrides.stream()
            .filter(override -> override.tenantId().equals(tenantId)
                && override.agentKey().equals(agentKey)
                && override.version() == version)
            .findFirst();
    }

    @Override
    public void save(AgentDefinitionOverride override) {
        overrides.removeIf(existing -> sameKey(existing, override));
        overrides.add(override);
    }

    @Override
    public boolean delete(TenantId tenantId, String agentKey, int version) {
        return overrides.removeIf(override -> override.tenantId().equals(tenantId)
            && override.agentKey().equals(agentKey)
            && override.version() == version);
    }

    @Override
    public int deleteByTenantAndAgentKey(TenantId tenantId, String agentKey) {
        int before = overrides.size();
        overrides.removeIf(override -> override.tenantId().equals(tenantId)
            && override.agentKey().equals(agentKey));
        return before - overrides.size();
    }

    List<AgentDefinitionOverride> all() {
        return List.copyOf(overrides);
    }

    private static boolean sameKey(AgentDefinitionOverride left, AgentDefinitionOverride right) {
        return left.tenantId().equals(right.tenantId())
            && left.agentKey().equals(right.agentKey())
            && left.version() == right.version();
    }
}
