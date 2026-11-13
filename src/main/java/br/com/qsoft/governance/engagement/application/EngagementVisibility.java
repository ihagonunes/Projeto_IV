package br.com.qsoft.governance.engagement.application;

import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.util.function.Predicate;

final class EngagementVisibility {

    private EngagementVisibility() {
    }

    static Predicate<UserContext> forScope(VisibilityScope visibility) {
        return switch (visibility) {
            case VisibilityScope.None ignored -> throw new EngagementAccessDeniedException();
            case VisibilityScope.TenantWide ignored -> context -> true;
            case VisibilityScope.Departments departments -> context -> departments.self().equals(context.userId())
                || (context.departmentId() != null && departments.ids().contains(context.departmentId()));
            case VisibilityScope.SelfOnly self -> context -> self.self().equals(context.userId());
        };
    }
}
