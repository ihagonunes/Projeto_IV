package br.com.qsoft.governance.agents.application;

import static br.com.qsoft.governance.agents.application.InMemoryAgentCatalog.definition;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.agents.domain.AgentDefinitionOverride;
import br.com.qsoft.governance.agents.domain.AgentDefinitionStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryAgentCatalogTest {

    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

    @Test
    void returnsGlobalCatalogWhenTenantHasNoOverride() {
        InMemoryAgentCatalog catalog = new InMemoryAgentCatalog(
            definition("policies", 1, AgentDefinitionStatus.ACTIVE),
            definition("code", 1, AgentDefinitionStatus.DRAFT));
        QueryAgentCatalog query = new QueryAgentCatalog(catalog, new InMemoryAgentOverrides());

        List<AgentCatalogEntry> entries = query.query(TenantId.random());

        assertThat(entries).hasSize(2);
        AgentCatalogEntry code = entries.stream()
            .filter(entry -> entry.agentKey().equals("code")).findFirst().orElseThrow();
        assertThat(code.status()).isEqualTo(AgentDefinitionStatus.DRAFT);
        assertThat(code.globalStatus()).isEqualTo(AgentDefinitionStatus.DRAFT);
        assertThat(code.executable()).isFalse();
        assertThat(code.overridden()).isFalse();
        assertThat(code.overrideUpdatedAt()).isNull();
    }

    @Test
    void overrideChangesEffectiveStatusAndExecutability() {
        InMemoryAgentCatalog catalog = new InMemoryAgentCatalog(
            definition("code", 1, AgentDefinitionStatus.DRAFT));
        InMemoryAgentOverrides overrides = new InMemoryAgentOverrides();
        TenantId tenant = TenantId.random();
        overrides.save(new AgentDefinitionOverride(tenant, "code", 1, "Prompt do tenant",
            "[]", "{}", "{}", AgentDefinitionStatus.ACTIVE, NOW));
        QueryAgentCatalog query = new QueryAgentCatalog(catalog, overrides);

        AgentCatalogEntry entry = query.query(tenant).get(0);

        assertThat(entry.status()).isEqualTo(AgentDefinitionStatus.ACTIVE);
        assertThat(entry.globalStatus()).isEqualTo(AgentDefinitionStatus.DRAFT);
        assertThat(entry.executable()).isTrue();
        assertThat(entry.overridden()).isTrue();
        assertThat(entry.overrideUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void overrideOfAnotherTenantIsIgnored() {
        InMemoryAgentCatalog catalog = new InMemoryAgentCatalog(
            definition("code", 1, AgentDefinitionStatus.DRAFT));
        InMemoryAgentOverrides overrides = new InMemoryAgentOverrides();
        overrides.save(new AgentDefinitionOverride(TenantId.random(), "code", 1, null,
            null, null, null, AgentDefinitionStatus.ACTIVE, NOW));
        QueryAgentCatalog query = new QueryAgentCatalog(catalog, overrides);

        AgentCatalogEntry entry = query.query(TenantId.random()).get(0);

        assertThat(entry.status()).isEqualTo(AgentDefinitionStatus.DRAFT);
        assertThat(entry.overridden()).isFalse();
    }

    @Test
    void archivedOverrideMakesAgentNotExecutable() {
        InMemoryAgentCatalog catalog = new InMemoryAgentCatalog(
            definition("policies", 1, AgentDefinitionStatus.ACTIVE));
        InMemoryAgentOverrides overrides = new InMemoryAgentOverrides();
        TenantId tenant = TenantId.random();
        overrides.save(new AgentDefinitionOverride(tenant, "policies", 1, null,
            null, null, null, AgentDefinitionStatus.ARCHIVED, NOW));
        QueryAgentCatalog query = new QueryAgentCatalog(catalog, overrides);

        AgentCatalogEntry entry = query.query(tenant).get(0);

        assertThat(entry.status()).isEqualTo(AgentDefinitionStatus.ARCHIVED);
        assertThat(entry.executable()).isFalse();
    }

    @Test
    void returnsOnlyTheLatestVersionPerAgentKey() {
        InMemoryAgentCatalog catalog = new InMemoryAgentCatalog(
            definition("policies", 2, AgentDefinitionStatus.DRAFT),
            definition("policies", 1, AgentDefinitionStatus.ACTIVE));
        QueryAgentCatalog query = new QueryAgentCatalog(catalog, new InMemoryAgentOverrides());

        List<AgentCatalogEntry> entries = query.query(TenantId.random());

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).version()).isEqualTo(2);
        assertThat(entries.get(0).executable()).isFalse();
    }
}
