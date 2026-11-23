package br.com.qsoft.governance.agents.application;

import static br.com.qsoft.governance.agents.application.InMemoryAgentCatalog.definition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.agents.domain.AgentDefinitionOverride;
import br.com.qsoft.governance.agents.domain.AgentDefinitionStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class UpsertAgentOverrideTest {

    private static final Instant EXPECTED = Instant.parse("2026-11-21T12:00:00Z");

    private final InMemoryAgentCatalog catalog = new InMemoryAgentCatalog(
        definition("policies", 1, AgentDefinitionStatus.ACTIVE),
        definition("code", 1, AgentDefinitionStatus.DRAFT));
    private final InMemoryAgentOverrides overrides = new InMemoryAgentOverrides();
    private final UpsertAgentOverride useCase = new UpsertAgentOverride(catalog, overrides,
        new AgentPolicyJson(new ObjectMapper()));
    private final TenantId tenant = TenantId.random();

    @Test
    void createsOverrideAndReturnsEffectiveEntry() {
        AgentCatalogEntry entry = useCase.upsert(new AgentOverrideCommand(tenant, "code", 1,
            "Prompt do tenant", "[]", "{\"mode\":\"NONE\"}", "{\"mode\":\"INHERIT\"}",
            AgentDefinitionStatus.ACTIVE, null));

        assertThat(entry.agentKey()).isEqualTo("code");
        assertThat(entry.status()).isEqualTo(AgentDefinitionStatus.ACTIVE);
        assertThat(entry.executable()).isTrue();
        assertThat(entry.overridden()).isTrue();
        assertThat(entry.overrideUpdatedAt()).isNotNull();
        assertThat(overrides.find(tenant, "code", 1)).get()
            .extracting(AgentDefinitionOverride::promptTemplate)
            .isEqualTo("Prompt do tenant");
    }

    @Test
    void acceptsPartialOverride() {
        AgentCatalogEntry entry = useCase.upsert(new AgentOverrideCommand(tenant, "code", 1,
            null, null, null, null, AgentDefinitionStatus.ACTIVE, null));

        assertThat(entry.executable()).isTrue();
        assertThat(overrides.find(tenant, "code", 1)).get()
            .satisfies(override -> {
                assertThat(override.promptTemplate()).isNull();
                assertThat(override.toolsJson()).isNull();
                assertThat(override.status()).isEqualTo(AgentDefinitionStatus.ACTIVE);
            });
    }

    @Test
    void rejectsUnknownAgentKeyOrVersionWithNotFound() {
        assertThatThrownBy(() -> useCase.upsert(new AgentOverrideCommand(tenant, "missing", 1,
            "p", null, null, null, null, null)))
            .isInstanceOf(AgentCatalogEntryNotFoundException.class);
        assertThatThrownBy(() -> useCase.upsert(new AgentOverrideCommand(tenant, "policies", 2,
            "p", null, null, null, null, null)))
            .isInstanceOf(AgentCatalogEntryNotFoundException.class);
    }

    @Test
    void rejectsInvalidRequestWithoutMutation() {
        assertThatThrownBy(() -> useCase.upsert(new AgentOverrideCommand(tenant, " ", 1,
            "p", null, null, null, null, null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.upsert(new AgentOverrideCommand(tenant, "policies", 0,
            "p", null, null, null, null, null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.upsert(new AgentOverrideCommand(tenant, "policies", 1,
            null, null, null, null, null, null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(overrides.all()).isEmpty();
    }

    @Test
    void staleExpectedTimestampConflicts() {
        useCase.upsert(new AgentOverrideCommand(tenant, "policies", 1, "v1", null, null, null,
            null, null));

        assertThatThrownBy(() -> useCase.upsert(new AgentOverrideCommand(tenant, "policies", 1,
            "v2", null, null, null, null, Instant.EPOCH)))
            .isInstanceOf(AgentOverrideConflictException.class);
    }

    @Test
    void expectedTimestampWithoutExistingOverrideConflicts() {
        assertThatThrownBy(() -> useCase.upsert(new AgentOverrideCommand(tenant, "policies", 1,
            "v1", null, null, null, null, EXPECTED)))
            .isInstanceOf(AgentOverrideConflictException.class);
    }

    @Test
    void matchingExpectedTimestampUpdates() {
        AgentCatalogEntry created = useCase.upsert(new AgentOverrideCommand(tenant, "policies", 1,
            "v1", null, null, null, null, null));

        AgentCatalogEntry updated = useCase.upsert(new AgentOverrideCommand(tenant, "policies", 1,
            "v2", null, null, null, null, created.overrideUpdatedAt()));

        assertThat(updated.overrideUpdatedAt()).isAfterOrEqualTo(created.overrideUpdatedAt());
        assertThat(overrides.find(tenant, "policies", 1)).get()
            .extracting(AgentDefinitionOverride::promptTemplate)
            .isEqualTo("v2");
    }

    @Test
    void timestampIsComparedAtMillisecondPrecision() {
        overrides.save(new AgentDefinitionOverride(tenant, "policies", 1, "v1",
            null, null, null, null, Instant.parse("2026-11-21T12:00:00.123456Z")));

        AgentCatalogEntry updated = useCase.upsert(new AgentOverrideCommand(tenant, "policies", 1,
            "v2", null, null, null, null, Instant.parse("2026-11-21T12:00:00.123Z")));

        assertThat(updated.overrideUpdatedAt()).isNotNull();

        assertThatThrownBy(() -> useCase.upsert(new AgentOverrideCommand(tenant, "policies", 1,
            "v3", null, null, null, null, Instant.parse("2026-11-21T12:00:00.124Z"))))
            .isInstanceOf(AgentOverrideConflictException.class);
    }

    @Test
    void rejectsPolicyVocabularyOutsideTheClosedContract() {
        assertThatThrownBy(() -> useCase.upsert(new AgentOverrideCommand(tenant, "policies", 1,
            null, "{}", null, null, null, null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.upsert(new AgentOverrideCommand(tenant, "policies", 1,
            null, "[1]", null, null, null, null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.upsert(new AgentOverrideCommand(tenant, "policies", 1,
            null, null, "{\"mode\":\"TENANT\",\"topics\":[\"x\"]}", null, null, null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.upsert(new AgentOverrideCommand(tenant, "policies", 1,
            null, null, null, "{\"mode\":\"EXPLICIT\"}", null, null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(overrides.all()).isEmpty();
    }
}
