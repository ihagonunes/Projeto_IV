package br.com.qsoft.governance.agents.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.agents.domain.AgentFlowStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentFlowAccessTest {

    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");
    private static final UUID DEPARTMENT = UUID.randomUUID();

    private final UserId owner = UserId.random();
    private final AgentFlow owned = AgentFlow.create(UUID.randomUUID(), TenantId.random(), owner,
        DEPARTMENT, "Próprio", NOW);
    private final AgentFlow other = AgentFlow.create(UUID.randomUUID(), owned.tenantId(),
        UserId.random(), UUID.randomUUID(), "Outro", NOW);

    @Test
    void tenantWideSeesEverything() {
        assertThat(AgentFlowAccess.visible(new VisibilityScope.TenantWide(), owned)).isTrue();
        assertThat(AgentFlowAccess.visible(new VisibilityScope.TenantWide(), other)).isTrue();
    }

    @Test
    void departmentsSeeOwnDepartmentOrOwnFlow() {
        VisibilityScope scope = new VisibilityScope.Departments(Set.of(DEPARTMENT), owner);

        assertThat(AgentFlowAccess.visible(scope, owned)).isTrue();
        assertThat(AgentFlowAccess.visible(scope, other)).isFalse();
    }

    @Test
    void selfOnlySeesOwnFlow() {
        VisibilityScope scope = new VisibilityScope.SelfOnly(owner);

        assertThat(AgentFlowAccess.visible(scope, owned)).isTrue();
        assertThat(AgentFlowAccess.visible(scope, other)).isFalse();
    }

    @Test
    void noneSeesNothing() {
        assertThat(AgentFlowAccess.visible(new VisibilityScope.None(), owned)).isFalse();
    }

    @Test
    void tenantWideFlowWithoutDepartmentIsVisibleToDepartmentScopeOnlyIfOwner() {
        AgentFlow tenantWide = AgentFlow.create(UUID.randomUUID(), owned.tenantId(), owner,
            null, "Sem depto", NOW);

        assertThat(AgentFlowAccess.visible(
            new VisibilityScope.Departments(Set.of(DEPARTMENT), owner), tenantWide)).isTrue();
        assertThat(AgentFlowAccess.visible(
            new VisibilityScope.Departments(Set.of(DEPARTMENT), UserId.random()), tenantWide))
            .isFalse();
    }

    @Test
    void statusIsIrrelevantForVisibility() {
        AgentFlow archived = new AgentFlow(owned.id(), owned.tenantId(), owner, DEPARTMENT,
            "Arquivado", AgentFlowStatus.ARCHIVED, null, 2L, NOW, NOW, null);

        assertThat(AgentFlowAccess.visible(new VisibilityScope.SelfOnly(owner), archived)).isTrue();
    }
}
