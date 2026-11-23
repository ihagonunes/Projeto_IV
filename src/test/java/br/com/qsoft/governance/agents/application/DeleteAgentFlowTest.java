package br.com.qsoft.governance.agents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.agents.domain.AgentFlowStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeleteAgentFlowTest {

    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");

    private final TenantId tenant = TenantId.random();
    private final UserId actor = UserId.random();
    private final InMemoryAgentFlows flows = new InMemoryAgentFlows();
    private final FixedAuthorization authorization = new FixedAuthorization();
    private final DeleteAgentFlow useCase = new DeleteAgentFlow(flows, authorization);

    @Test
    void softDeletesTheFlow() {
        authorization.scope(new VisibilityScope.TenantWide());
        AgentFlow flow = seedFlow();

        useCase.delete(tenant, actor, flow.id());

        assertThat(flows.findById(tenant, flow.id())).isEmpty();
        assertThat(flows.flows()).singleElement()
            .satisfies(deleted -> assertThat(deleted.deletedAt()).isNotNull());
    }

    @Test
    void outsideScopeIsNotFound() {
        AgentFlow flow = seedFlow();
        authorization.scope(new VisibilityScope.SelfOnly(UserId.random()));

        assertThatThrownBy(() -> useCase.delete(tenant, actor, flow.id()))
            .isInstanceOf(AgentFlowNotFoundException.class);
        assertThat(flows.findById(tenant, flow.id())).isPresent();
    }

    @Test
    void unknownIdIsNotFound() {
        authorization.scope(new VisibilityScope.TenantWide());

        assertThatThrownBy(() -> useCase.delete(tenant, actor, UUID.randomUUID()))
            .isInstanceOf(AgentFlowNotFoundException.class);
    }

    private AgentFlow seedFlow() {
        return flows.save(AgentFlow.create(UUID.randomUUID(), tenant, actor, null, "Fluxo", NOW));
    }
}
