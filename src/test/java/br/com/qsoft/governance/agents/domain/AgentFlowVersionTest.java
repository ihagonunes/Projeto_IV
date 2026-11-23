package br.com.qsoft.governance.agents.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentFlowVersionTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    @Test
    void keepsGraphSnapshotAndValidation() {
        AgentFlowVersion version = new AgentFlowVersion(UUID.randomUUID(), UUID.randomUUID(),
            1, 1, "{\"schemaVersion\":1,\"nodes\":[],\"edges\":[]}",
            AgentValidationStatus.INVALID, UserId.random(), NOW);

        assertThat(version.schemaVersion()).isEqualTo(1);
        assertThat(version.validationStatus()).isEqualTo(AgentValidationStatus.INVALID);
        assertThat(version.graphJson()).contains("schemaVersion");
    }

    @Test
    void rejectsInvalidStructure() {
        assertThatThrownBy(() -> new AgentFlowVersion(UUID.randomUUID(), UUID.randomUUID(),
            0, 1, "{}", AgentValidationStatus.VALID, UserId.random(), NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentFlowVersion(UUID.randomUUID(), UUID.randomUUID(),
            1, 0, "{}", AgentValidationStatus.VALID, UserId.random(), NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentFlowVersion(UUID.randomUUID(), UUID.randomUUID(),
            1, 1, " ", AgentValidationStatus.VALID, UserId.random(), NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentFlowVersion(UUID.randomUUID(), UUID.randomUUID(),
            1, 1, "{}", null, UserId.random(), NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AgentFlowVersion(UUID.randomUUID(), UUID.randomUUID(),
            1, 1, "{}", AgentValidationStatus.VALID, null, NOW))
            .isInstanceOf(NullPointerException.class);
    }
}
