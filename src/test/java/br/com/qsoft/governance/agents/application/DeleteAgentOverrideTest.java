package br.com.qsoft.governance.agents.application;

import static br.com.qsoft.governance.agents.application.InMemoryAgentCatalog.definition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.agents.domain.AgentDefinitionOverride;
import br.com.qsoft.governance.agents.domain.AgentDefinitionStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DeleteAgentOverrideTest {

    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

    private final InMemoryAgentCatalog catalog = new InMemoryAgentCatalog(
        definition("policies", 2, AgentDefinitionStatus.DRAFT),
        definition("policies", 1, AgentDefinitionStatus.ACTIVE));
    private final InMemoryAgentOverrides overrides = new InMemoryAgentOverrides();
    private final DeleteAgentOverride useCase = new DeleteAgentOverride(catalog, overrides);
    private final TenantId tenant = TenantId.random();

    @Test
    void removesEveryVersionOfTheOverrideForTheTenant() {
        overrides.save(new AgentDefinitionOverride(tenant, "policies", 1,
            "p1", null, null, null, null, NOW));
        overrides.save(new AgentDefinitionOverride(tenant, "policies", 2,
            "p2", null, null, null, null, NOW));

        int removed = useCase.delete(tenant, "policies");

        assertThat(removed).isEqualTo(2);
        assertThat(overrides.findByTenant(tenant)).isEmpty();
    }

    @Test
    void doesNotRemoveAnotherTenantOverride() {
        TenantId other = TenantId.random();
        overrides.save(new AgentDefinitionOverride(other, "policies", 1,
            "outro", null, null, null, null, NOW));

        useCase.delete(tenant, "policies");

        assertThat(overrides.findByTenant(other)).hasSize(1);
    }

    @Test
    void unknownAgentKeyIsNotFound() {
        assertThatThrownBy(() -> useCase.delete(tenant, "missing"))
            .isInstanceOf(AgentCatalogEntryNotFoundException.class);
    }

    @Test
    void deletingWithoutOverrideIsIdempotent() {
        assertThat(useCase.delete(tenant, "policies")).isZero();
    }
}
