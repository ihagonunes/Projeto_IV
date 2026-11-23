package br.com.qsoft.governance.agents.application;

import static br.com.qsoft.governance.agents.application.InMemoryAgentCatalog.definition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.agents.domain.AgentDefinitionStatus;
import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.agents.domain.AgentFlowStatus;
import br.com.qsoft.governance.agents.domain.AgentValidationStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CreateAgentFlowTest {

    private static final String GRAPH =
        "{\"schemaVersion\":1,\"nodes\":[{\"id\":\"step-1\",\"kind\":\"task\"}],\"edges\":[]}";

    private final TenantId tenant = TenantId.random();
    private final UserId actor = UserId.random();
    private final UUID department = UUID.randomUUID();
    private final InMemoryAgentFlows flows = new InMemoryAgentFlows();
    private final InMemoryAgentCatalog catalog = new InMemoryAgentCatalog(
        definition("policies", 1, AgentDefinitionStatus.ACTIVE));
    private final FixedAuthorization authorization = new FixedAuthorization();
    private final FixedIdentity identity = new FixedIdentity(tenant, department);
    private final ValidateAgentFlowGraph validator = new ValidateAgentFlowGraph(
        new AgentFlowGraphJson(new ObjectMapper()),
        new QueryAgentCatalog(catalog, new InMemoryAgentOverrides()),
        flows, authorization);
    private final CreateAgentFlow useCase = new CreateAgentFlow(flows, flows, authorization,
        identity, validator);

    @Test
    void createsDraftWithFirstInvalidVersionOwnedByTheActor() {
        authorization.scope(new VisibilityScope.Departments(Set.of(department), actor));

        AgentFlowDetail detail = useCase.create(new CreateAgentFlowCommand(
            tenant, actor, "Fluxo básico", null, 1, GRAPH));

        assertThat(detail.flow().tenantId()).isEqualTo(tenant);
        assertThat(detail.flow().ownerUserId()).isEqualTo(actor);
        assertThat(detail.flow().departmentId()).isEqualTo(department);
        assertThat(detail.flow().status()).isEqualTo(AgentFlowStatus.DRAFT);
        assertThat(detail.flow().rowVersion()).isEqualTo(1L);
        assertThat(detail.flow().currentVersionId()).isEqualTo(detail.currentVersion().id());
        assertThat(detail.currentVersion().versionNumber()).isEqualTo(1);
        assertThat(detail.currentVersion().validationStatus()).isEqualTo(AgentValidationStatus.INVALID);
        assertThat(detail.currentVersion().createdBy()).isEqualTo(actor);
        assertThat(detail.currentVersion().graphJson()).contains("\"step-1\"");
    }

    @Test
    void tenantWideActorCanCreateWithoutDepartment() {
        authorization.scope(new VisibilityScope.TenantWide());
        identity.departmentId(null);

        AgentFlowDetail detail = useCase.create(new CreateAgentFlowCommand(
            tenant, actor, "Fluxo do tenant", null, 1, GRAPH));

        assertThat(detail.flow().departmentId()).isNull();
    }

    @Test
    void rejectsDepartmentOutsideTheManageScope() {
        authorization.scope(new VisibilityScope.Departments(Set.of(department), actor));

        assertThatThrownBy(() -> useCase.create(new CreateAgentFlowCommand(
            tenant, actor, "Outro depto", UUID.randomUUID(), 1, GRAPH)))
            .isInstanceOf(AgentFlowAccessDeniedException.class);
        assertThat(flows.flows()).isEmpty();
    }

    @Test
    void rejectsInvalidNameAndStructurallyInvalidGraph() {
        authorization.scope(new VisibilityScope.TenantWide());

        assertThatThrownBy(() -> useCase.create(new CreateAgentFlowCommand(
            tenant, actor, " ", null, 1, GRAPH)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.create(new CreateAgentFlowCommand(
            tenant, actor, "Fluxo", null, 0, GRAPH)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.create(new CreateAgentFlowCommand(
            tenant, actor, "Fluxo", null, 1, "[]")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(flows.flows()).isEmpty();
    }

    @Test
    void rejectsActorWithoutManageVisibility() {
        authorization.scope(new VisibilityScope.None());

        assertThatThrownBy(() -> useCase.create(new CreateAgentFlowCommand(
            tenant, actor, "Fluxo", null, 1, GRAPH)))
            .isInstanceOf(AgentFlowAccessDeniedException.class);
        assertThat(flows.flows()).isEmpty();
    }

    @Test
    void agentNodesAreValidatedAgainstTheEffectiveCatalog() {
        authorization.scope(new VisibilityScope.TenantWide());
        String known = "{\"schemaVersion\":1,\"nodes\":["
            + "{\"id\":\"n1\",\"kind\":\"agent\",\"agentKey\":\"policies\",\"x\":0.5,\"y\":0.5}],"
            + "\"edges\":[]}";
        assertThat(useCase.create(new CreateAgentFlowCommand(
            tenant, actor, "Com agente", null, 1, known))
            .currentVersion().validationStatus()).isEqualTo(AgentValidationStatus.INVALID);

        String unknown = "{\"schemaVersion\":1,\"nodes\":["
            + "{\"id\":\"n1\",\"kind\":\"agent\",\"agentKey\":\"merge\",\"x\":0.5,\"y\":0.5}],"
            + "\"edges\":[]}";
        assertThat(useCase.create(new CreateAgentFlowCommand(
            tenant, actor, "Sem catálogo", null, 1, unknown))
            .currentVersion().validationStatus()).isEqualTo(AgentValidationStatus.INVALID);
    }

    @Test
    void serverIdentityNeverComesFromTheGraph() {
        authorization.scope(new VisibilityScope.TenantWide());

        AgentFlow flow = useCase.create(new CreateAgentFlowCommand(
            tenant, actor, "Fluxo", null, 1, GRAPH)).flow();

        assertThat(flow.id().toString()).isNotEqualTo("step-1");
        assertThat(flow.currentVersionId().toString()).isNotEqualTo("step-1");
    }
}
