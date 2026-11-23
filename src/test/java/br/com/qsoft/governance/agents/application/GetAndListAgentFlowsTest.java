package br.com.qsoft.governance.agents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.agents.domain.AgentFlowStatus;
import br.com.qsoft.governance.agents.domain.AgentFlowVersion;
import br.com.qsoft.governance.agents.domain.AgentValidationStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GetAndListAgentFlowsTest {

    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");
    private static final String GRAPH = "{\"schemaVersion\":1,\"nodes\":[],\"edges\":[]}";

    private final TenantId tenant = TenantId.random();
    private final UserId actor = UserId.random();
    private final InMemoryAgentFlows flows = new InMemoryAgentFlows();
    private final FixedAuthorization authorization = new FixedAuthorization();
    private final InMemoryAgentCatalog catalog = new InMemoryAgentCatalog();
    private final ValidateAgentFlowGraph validator = new ValidateAgentFlowGraph(
        new AgentFlowGraphJson(new ObjectMapper()),
        new QueryAgentCatalog(catalog, new InMemoryAgentOverrides()),
        flows, authorization);
    private final GetAgentFlow getUseCase = new GetAgentFlow(flows, flows, authorization);
    private final ListAgentFlows listUseCase = new ListAgentFlows(flows, authorization);
    private final CreateAgentFlow createUseCase = new CreateAgentFlow(flows, flows, authorization,
        new FixedIdentity(tenant, null), validator);

    @Test
    void detailReturnsCurrentVersionAndGraph() {
        authorization.scope(new VisibilityScope.TenantWide());
        AgentFlow flow = createUseCase.create(new CreateAgentFlowCommand(
            tenant, actor, "Fluxo", null, 1, GRAPH)).flow();

        AgentFlowDetail detail = getUseCase.get(tenant, actor, flow.id());

        assertThat(detail.flow().id()).isEqualTo(flow.id());
        assertThat(detail.currentVersion().versionNumber()).isEqualTo(1);
        assertThat(detail.currentVersion().graphJson()).isEqualTo(GRAPH);
    }

    @Test
    void detailOutsideScopeIsNotFound() {
        authorization.scope(new VisibilityScope.SelfOnly(UserId.random()));
        AgentFlow flow = seedFlow(actor, AgentFlowStatus.DRAFT);

        assertThatThrownBy(() -> getUseCase.get(tenant, actor, flow.id()))
            .isInstanceOf(AgentFlowNotFoundException.class);
    }

    @Test
    void listClampsPaginationAndFiltersByStatus() {
        authorization.scope(new VisibilityScope.TenantWide());
        seedFlow(actor, AgentFlowStatus.DRAFT);
        seedFlow(actor, AgentFlowStatus.ARCHIVED);

        PageResult<AgentFlow> page = listUseCase.list(tenant, actor, null, -3, 500);

        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(100);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).hasSize(2);

        PageResult<AgentFlow> drafts = listUseCase.list(tenant, actor, AgentFlowStatus.DRAFT, 0, 20);
        assertThat(drafts.content()).allMatch(flow -> flow.status() == AgentFlowStatus.DRAFT);
        assertThat(drafts.totalElements()).isEqualTo(1);
    }

    @Test
    void selfOnlyListsOnlyOwnFlows() {
        AgentFlow own = seedFlow(actor, AgentFlowStatus.DRAFT);
        seedFlow(UserId.random(), AgentFlowStatus.DRAFT);
        authorization.scope(new VisibilityScope.SelfOnly(actor));

        PageResult<AgentFlow> page = listUseCase.list(tenant, actor, null, 0, 20);

        assertThat(page.content()).extracting(AgentFlow::id).containsExactly(own.id());
    }

    @Test
    void noVisibilityIsDenied() {
        authorization.scope(new VisibilityScope.None());

        assertThatThrownBy(() -> listUseCase.list(tenant, actor, null, 0, 20))
            .isInstanceOf(AgentFlowAccessDeniedException.class);
    }

    private AgentFlow seedFlow(UserId owner, AgentFlowStatus status) {
        UUID id = UUID.randomUUID();
        AgentFlow flow = flows.save(new AgentFlow(id, tenant, owner, null, "Fluxo " + id,
            status, null, 1L, NOW, NOW, null));
        AgentFlowVersion version = flows.save(tenant, new AgentFlowVersion(UUID.randomUUID(),
            id, 1, 1, GRAPH, AgentValidationStatus.INVALID, owner, NOW));
        return flows.save(new AgentFlow(id, tenant, owner, null, flow.name(), status,
            version.id(), 1L, NOW, NOW, null));
    }
}
