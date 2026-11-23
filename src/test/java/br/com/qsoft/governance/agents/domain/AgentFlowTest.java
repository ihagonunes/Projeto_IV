package br.com.qsoft.governance.agents.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentFlowTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    @Test
    void createProducesDraftAtRowVersionOne() {
        UUID id = UUID.randomUUID();
        AgentFlow flow = AgentFlow.create(id, TenantId.random(), UserId.random(),
            UUID.randomUUID(), "Fluxo de análise", NOW);

        assertThat(flow.id()).isEqualTo(id);
        assertThat(flow.status()).isEqualTo(AgentFlowStatus.DRAFT);
        assertThat(flow.currentVersionId()).isNull();
        assertThat(flow.rowVersion()).isEqualTo(1L);
        assertThat(flow.deletedAt()).isNull();
        assertThat(flow.createdAt()).isEqualTo(NOW);
    }

    @Test
    void allowsTenantWideFlowWithoutDepartment() {
        AgentFlow flow = AgentFlow.create(UUID.randomUUID(), TenantId.random(), UserId.random(),
            null, "Fluxo do tenant", NOW);

        assertThat(flow.departmentId()).isNull();
    }

    @Test
    void publishedFlowRequiresCurrentVersion() {
        assertThatThrownBy(() -> new AgentFlow(UUID.randomUUID(), TenantId.random(),
            UserId.random(), null, "Fluxo", AgentFlowStatus.PUBLISHED, null, 2L, NOW, NOW, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("currentVersionId");
    }

    @Test
    void rejectsInvalidStructure() {
        assertThatThrownBy(() -> AgentFlow.create(UUID.randomUUID(), null, UserId.random(),
            null, "Fluxo", NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AgentFlow.create(UUID.randomUUID(), TenantId.random(), null,
            null, "Fluxo", NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AgentFlow.create(UUID.randomUUID(), TenantId.random(),
            UserId.random(), null, " ", NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentFlow(UUID.randomUUID(), TenantId.random(),
            UserId.random(), null, "Fluxo", AgentFlowStatus.DRAFT, null, 0L, NOW, NOW, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
