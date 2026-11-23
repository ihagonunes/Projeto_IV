package br.com.qsoft.governance.agents.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentDefinitionTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    @Test
    void createProducesDraftWithEmptyPolicies() {
        AgentDefinition definition = AgentDefinition.create(UUID.randomUUID(), "writing", 1,
            "Redator", "Escreve textos", "writing",
            "Você é um redator.", NOW);

        assertThat(definition.status()).isEqualTo(AgentDefinitionStatus.DRAFT);
        assertThat(definition.toolsJson()).isEqualTo("[]");
        assertThat(definition.knowledgeScopeJson()).isEqualTo("{}");
        assertThat(definition.modelPolicyJson()).isEqualTo("{}");
        assertThat(definition.createdAt()).isEqualTo(NOW);
        assertThat(definition.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void trimsAgentKeyAndName() {
        AgentDefinition definition = AgentDefinition.create(UUID.randomUUID(), "  data  ", 1,
            "  Dados  ", "desc", "data", "prompt", NOW);

        assertThat(definition.agentKey()).isEqualTo("data");
        assertThat(definition.name()).isEqualTo("Dados");
    }

    @Test
    void rejectsInvalidIdentityAndVersion() {
        assertThatThrownBy(() -> AgentDefinition.create(UUID.randomUUID(), " ", 1,
            "Nome", "desc", "cat", "prompt", NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgentDefinition.create(UUID.randomUUID(), "data", 0,
            "Nome", "desc", "cat", "prompt", NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgentDefinition.create(null, "data", 1,
            "Nome", "desc", "cat", "prompt", NOW))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsMissingRequiredFields() {
        assertThatThrownBy(() -> new AgentDefinition(UUID.randomUUID(), "data", 1,
            "Nome", "desc", "cat", "prompt", "[]", "{}", "{}", null, NOW, NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AgentDefinition(UUID.randomUUID(), "data", 1,
            "Nome", "desc", "cat", "prompt", " ", "{}", "{}",
            AgentDefinitionStatus.ACTIVE, NOW, NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOverlongName() {
        String longName = "x".repeat(121);

        assertThatThrownBy(() -> AgentDefinition.create(UUID.randomUUID(), "data", 1,
            longName, "desc", "cat", "prompt", NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
