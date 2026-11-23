package br.com.qsoft.governance.agents.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AgentDefinitionOverrideTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    @Test
    void inheritingLeavesEveryFieldNull() {
        AgentDefinitionOverride override = AgentDefinitionOverride.inheriting(
            TenantId.random(), "data", 1, NOW);

        assertThat(override.promptTemplate()).isNull();
        assertThat(override.toolsJson()).isNull();
        assertThat(override.knowledgeScopeJson()).isNull();
        assertThat(override.modelPolicyJson()).isNull();
        assertThat(override.status()).isNull();
        assertThat(override.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void allowsPartialOverride() {
        AgentDefinitionOverride override = new AgentDefinitionOverride(
            TenantId.random(), "data", 1, "Prompt do tenant", null, "{\"scope\":\"tenant\"}",
            null, AgentDefinitionStatus.ACTIVE, NOW);

        assertThat(override.promptTemplate()).isEqualTo("Prompt do tenant");
        assertThat(override.knowledgeScopeJson()).isEqualTo("{\"scope\":\"tenant\"}");
        assertThat(override.status()).isEqualTo(AgentDefinitionStatus.ACTIVE);
    }

    @Test
    void requiresTenantKeyVersionAndTimestamp() {
        assertThatThrownBy(() -> AgentDefinitionOverride.inheriting(null, "data", 1, NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AgentDefinitionOverride.inheriting(TenantId.random(), " ", 1, NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgentDefinitionOverride.inheriting(TenantId.random(), "data", 0, NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgentDefinitionOverride.inheriting(TenantId.random(), "data", 1, null))
            .isInstanceOf(NullPointerException.class);
    }
}
