package br.com.qsoft.governance.agents.application;

import static br.com.qsoft.governance.agents.application.InMemoryAgentCatalog.definition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.agents.domain.AgentDefinitionStatus;
import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.agents.domain.AgentFlowStatus;
import br.com.qsoft.governance.agents.domain.AgentFlowVersion;
import br.com.qsoft.governance.agents.domain.AgentValidationStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PublishAgentFlowTest {

    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");
    private static final String VALID_GRAPH = """
        {"schemaVersion":1,"nodes":[
          {"id":"t","kind":"task","x":0,"y":0},
          {"id":"a","kind":"agent","agentKey":"policies","x":0.5,"y":0.5},
          {"id":"r","kind":"result","x":1,"y":1}
        ],"edges":[{"from":"t","to":"a"},{"from":"a","to":"r"}]}
        """;

    private final TenantId tenant = TenantId.random();
    private final UserId actor = UserId.random();
    private final UUID department = UUID.randomUUID();
    private final InMemoryAgentFlows flows = new InMemoryAgentFlows();
    private final FixedAuthorization authorization = new FixedAuthorization();

    private final InMemoryAgentCatalog catalog = new InMemoryAgentCatalog(
        definition("policies", 1, AgentDefinitionStatus.ACTIVE));
    private final ValidateAgentFlowGraph validator = validator(catalog);
    private final PublishAgentFlow publish = new PublishAgentFlow(flows, flows, authorization,
        validator);

    @Test
    void publishesTheValidCurrentVersion() {
        authorization.scope(new VisibilityScope.TenantWide());
        AgentFlow flow = seed(AgentValidationStatus.VALID, AgentFlowStatus.DRAFT, 1L);

        AgentFlowDetail detail = publish.publish(tenant, actor, flow.id(), 1L);

        assertThat(detail.flow().status()).isEqualTo(AgentFlowStatus.PUBLISHED);
        assertThat(detail.flow().rowVersion()).isEqualTo(2L);
        assertThat(detail.currentVersion().id()).isEqualTo(flow.currentVersionId());
        assertThat(detail.currentVersion().validationStatus()).isEqualTo(AgentValidationStatus.VALID);
        assertThat(flows.versions()).hasSize(1);
    }

    @Test
    void rejectsInvalidOrMissingCurrentVersion() {
        authorization.scope(new VisibilityScope.TenantWide());
        AgentFlow invalid = seed(AgentValidationStatus.INVALID, AgentFlowStatus.DRAFT, 1L);

        assertThatThrownBy(() -> publish.publish(tenant, actor, invalid.id(), 1L))
            .isInstanceOf(AgentFlowNotValidatedException.class);
    }

    @Test
    void revalidatesAgainstTheEffectiveCatalogAtPublishTime() {
        authorization.scope(new VisibilityScope.TenantWide());
        AgentFlow flow = seed(AgentValidationStatus.VALID, AgentFlowStatus.DRAFT, 1L);
        PublishAgentFlow withDeactivatedCatalog = new PublishAgentFlow(flows, flows,
            authorization, validator(new InMemoryAgentCatalog()));

        assertThatThrownBy(() -> withDeactivatedCatalog.publish(tenant, actor, flow.id(), 1L))
            .isInstanceOf(AgentFlowNotValidatedException.class);
    }

    @Test
    void alreadyPublishedIsAStateConflict() {
        authorization.scope(new VisibilityScope.TenantWide());
        AgentFlow flow = seed(AgentValidationStatus.VALID, AgentFlowStatus.PUBLISHED, 2L);

        assertThatThrownBy(() -> publish.publish(tenant, actor, flow.id(), 2L))
            .isInstanceOf(AgentFlowStateConflictException.class);
    }

    @Test
    void staleRowVersionIsAPreconditionFailure() {
        authorization.scope(new VisibilityScope.TenantWide());
        AgentFlow flow = seed(AgentValidationStatus.VALID, AgentFlowStatus.DRAFT, 1L);

        assertThatThrownBy(() -> publish.publish(tenant, actor, flow.id(), 9L))
            .isInstanceOf(AgentFlowPreconditionException.class);
    }

    @Test
    void requiresExpectedRowVersion() {
        authorization.scope(new VisibilityScope.TenantWide());
        AgentFlow flow = seed(AgentValidationStatus.VALID, AgentFlowStatus.DRAFT, 1L);

        assertThatThrownBy(() -> publish.publish(tenant, actor, flow.id(), null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void outsideScopeIsNotFound() {
        AgentFlow flow = seed(AgentValidationStatus.VALID, AgentFlowStatus.DRAFT, 1L);
        authorization.scope(new VisibilityScope.SelfOnly(UserId.random()));

        assertThatThrownBy(() -> publish.publish(tenant, actor, flow.id(), 1L))
            .isInstanceOf(AgentFlowNotFoundException.class);
    }

    private AgentFlow seed(AgentValidationStatus validationStatus, AgentFlowStatus status,
                           long rowVersion) {
        UUID versionId = UUID.randomUUID();
        AgentFlow flow = flows.save(new AgentFlow(UUID.randomUUID(), tenant, actor, department,
            "Fluxo", status, versionId, rowVersion, NOW, NOW, null));
        flows.save(tenant, new AgentFlowVersion(versionId, flow.id(), 1, 1, VALID_GRAPH,
            validationStatus, actor, NOW));
        return flow;
    }

    private ValidateAgentFlowGraph validator(InMemoryAgentCatalog source) {
        return new ValidateAgentFlowGraph(new AgentFlowGraphJson(new ObjectMapper()),
            new QueryAgentCatalog(source, new InMemoryAgentOverrides()), flows, authorization);
    }
}
