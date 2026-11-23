package br.com.qsoft.governance.agents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.agents.domain.AgentFlowStatus;
import br.com.qsoft.governance.agents.domain.AgentFlowVersion;
import br.com.qsoft.governance.agents.domain.AgentValidationStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SaveAgentFlowVersionTest {

    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");
    private static final String GRAPH_V1 = "{\"schemaVersion\":1,\"nodes\":[],\"edges\":[]}";
    private static final String GRAPH_V2 =
        "{\"schemaVersion\":1,\"nodes\":[{\"id\":\"n1\",\"kind\":\"task\"}],\"edges\":[]}";

    private final TenantId tenant = TenantId.random();
    private final UserId actor = UserId.random();
    private final UUID department = UUID.randomUUID();
    private final InMemoryAgentFlows flows = new InMemoryAgentFlows();
    private final InMemoryAgentCatalog catalog = new InMemoryAgentCatalog();
    private final FixedAuthorization authorization = new FixedAuthorization();
    private final ValidateAgentFlowGraph validator = new ValidateAgentFlowGraph(
        new AgentFlowGraphJson(new ObjectMapper()),
        new QueryAgentCatalog(catalog, new InMemoryAgentOverrides()),
        flows, authorization);
    private final SaveAgentFlowVersion useCase = new SaveAgentFlowVersion(flows, flows,
        authorization, validator);

    @Test
    void createsImmutableNextVersionAndBumpsRowVersion() {
        AgentFlow flow = seedFlow();
        authorization.scope(new VisibilityScope.TenantWide());

        AgentFlowDetail detail = useCase.save(new SaveAgentFlowVersionCommand(
            tenant, actor, flow.id(), 1L, "Fluxo renomeado", 2, GRAPH_V2));

        assertThat(detail.flow().rowVersion()).isEqualTo(2L);
        assertThat(detail.flow().name()).isEqualTo("Fluxo renomeado");
        assertThat(detail.currentVersion().versionNumber()).isEqualTo(2);
        assertThat(detail.currentVersion().schemaVersion()).isEqualTo(2);
        assertThat(detail.currentVersion().validationStatus()).isEqualTo(AgentValidationStatus.INVALID);
        assertThat(detail.flow().currentVersionId()).isEqualTo(detail.currentVersion().id());

        AgentFlowVersion first = flows.find(tenant, flow.id(), 1).orElseThrow();
        assertThat(first.graphJson()).isEqualTo(GRAPH_V1);
        assertThat(first.validationStatus()).isEqualTo(AgentValidationStatus.INVALID);
        assertThat(flows.versions()).hasSize(2);
    }

    @Test
    void keepsTheNameWhenItIsOmitted() {
        AgentFlow flow = seedFlow();
        authorization.scope(new VisibilityScope.TenantWide());

        AgentFlowDetail detail = useCase.save(new SaveAgentFlowVersionCommand(
            tenant, actor, flow.id(), 1L, null, 1, GRAPH_V2));

        assertThat(detail.flow().name()).isEqualTo("Fluxo");
    }

    @Test
    void requiresExpectedRowVersion() {
        AgentFlow flow = seedFlow();
        authorization.scope(new VisibilityScope.TenantWide());

        assertThatThrownBy(() -> useCase.save(new SaveAgentFlowVersionCommand(
            tenant, actor, flow.id(), null, null, 1, GRAPH_V2)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void staleRowVersionIsAPreconditionFailure() {
        AgentFlow flow = seedFlow();
        authorization.scope(new VisibilityScope.TenantWide());

        assertThatThrownBy(() -> useCase.save(new SaveAgentFlowVersionCommand(
            tenant, actor, flow.id(), 2L, null, 1, GRAPH_V2)))
            .isInstanceOf(AgentFlowPreconditionException.class);
        assertThat(flows.versions()).hasSize(1);
    }

    @Test
    void flowOutsideTheScopeOrTenantIsNotFound() {
        AgentFlow flow = seedFlow();
        authorization.scope(new VisibilityScope.SelfOnly(UserId.random()));

        assertThatThrownBy(() -> useCase.save(new SaveAgentFlowVersionCommand(
            tenant, actor, flow.id(), 1L, null, 1, GRAPH_V2)))
            .isInstanceOf(AgentFlowNotFoundException.class);
    }

    @Test
    void deletedFlowIsNotFound() {
        AgentFlow flow = seedFlow();
        flows.markDeleted(tenant, flow.id(), NOW);
        authorization.scope(new VisibilityScope.TenantWide());

        assertThatThrownBy(() -> useCase.save(new SaveAgentFlowVersionCommand(
            tenant, actor, flow.id(), 1L, null, 1, GRAPH_V2)))
            .isInstanceOf(AgentFlowNotFoundException.class);
    }

    @Test
    void invalidGraphDoesNotCreateAVersion() {
        AgentFlow flow = seedFlow();
        authorization.scope(new VisibilityScope.TenantWide());

        assertThatThrownBy(() -> useCase.save(new SaveAgentFlowVersionCommand(
            tenant, actor, flow.id(), 1L, null, 1, "[]")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(flows.versions()).hasSize(1);
    }

    @Test
    void agentNodeIsPersistedAsInvalidWhenNotActiveInTheCatalog() {
        AgentFlow flow = seedFlow();
        authorization.scope(new VisibilityScope.TenantWide());
        String unknown = "{\"schemaVersion\":1,\"nodes\":["
            + "{\"id\":\"n1\",\"kind\":\"agent\",\"agentKey\":\"policies\",\"x\":0.5,\"y\":0.5}],"
            + "\"edges\":[]}";

        AgentFlowDetail detail = useCase.save(new SaveAgentFlowVersionCommand(
            tenant, actor, flow.id(), 1L, null, 1, unknown));

        assertThat(detail.currentVersion().validationStatus())
            .isEqualTo(AgentValidationStatus.INVALID);
        assertThat(flows.versions()).hasSize(2);
    }

    @Test
    void publishedFlowRejectsNewVersions() {
        AgentFlow flow = seedFlow();
        AgentFlow published = new AgentFlow(flow.id(), flow.tenantId(), flow.ownerUserId(),
            flow.departmentId(), flow.name(), AgentFlowStatus.PUBLISHED, flow.currentVersionId(),
            2L, flow.createdAt(), NOW, null);
        flows.save(published);
        authorization.scope(new VisibilityScope.TenantWide());

        assertThatThrownBy(() -> useCase.save(new SaveAgentFlowVersionCommand(
            tenant, actor, flow.id(), 2L, null, 1, GRAPH_V2)))
            .isInstanceOf(AgentFlowStateConflictException.class);
    }

    private AgentFlow seedFlow() {
        AgentFlow flow = flows.save(AgentFlow.create(UUID.randomUUID(), tenant, actor,
            department, "Fluxo", NOW));
        AgentFlowVersion version = flows.save(tenant, new AgentFlowVersion(UUID.randomUUID(),
            flow.id(), 1, 1, GRAPH_V1, AgentValidationStatus.INVALID, actor, NOW));
        return flows.save(new AgentFlow(flow.id(), tenant, actor, department, "Fluxo",
            AgentFlowStatus.DRAFT, version.id(), 1L, NOW, NOW, null));
    }
}
