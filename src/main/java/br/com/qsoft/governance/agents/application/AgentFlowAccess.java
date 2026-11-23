package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;

/**
 * Regra única de visibilidade de um fluxo para o ator (ADR-36/ADR-44).
 * `TENANT` vê tudo; `DEPARTMENT` vê o próprio departamento ou o que é seu;
 * `SELF` vê somente o que é seu; `None` não vê nada.
 */
final class AgentFlowAccess {

    private AgentFlowAccess() {
    }

    static boolean visible(VisibilityScope scope, AgentFlow flow) {
        if (scope instanceof VisibilityScope.TenantWide) {
            return true;
        }
        if (scope instanceof VisibilityScope.Departments departments) {
            return flow.ownerUserId().equals(departments.self())
                || (flow.departmentId() != null
                    && departments.ids().contains(flow.departmentId()));
        }
        if (scope instanceof VisibilityScope.SelfOnly self) {
            return flow.ownerUserId().equals(self.self());
        }
        return false;
    }
}
