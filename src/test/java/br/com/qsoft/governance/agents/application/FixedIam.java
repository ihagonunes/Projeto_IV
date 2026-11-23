package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.AccessTarget;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.util.Optional;
import java.util.UUID;

/** IAM determinístico para os testes dos casos de uso de fluxo. */
final class FixedAuthorization implements AuthorizationQuery {

    private VisibilityScope scope;

    void scope(VisibilityScope scope) {
        this.scope = scope;
    }

    @Override
    public boolean isAllowed(TenantId tenantId, UserId actor, Resource resource, Action action,
                             AccessTarget target) {
        return scope != null && !(scope instanceof VisibilityScope.None);
    }

    @Override
    public VisibilityScope visibilityFor(TenantId tenantId, UserId actor, Resource resource,
                                         Action action) {
        return scope == null ? new VisibilityScope.None() : scope;
    }
}

final class FixedIdentity implements IdentityQuery {

    private final TenantId tenantId;
    private UUID departmentId;

    FixedIdentity(TenantId tenantId, UUID departmentId) {
        this.tenantId = tenantId;
        this.departmentId = departmentId;
    }

    void departmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    @Override
    public Optional<UserContext> getUserContext(UserId userId) {
        return Optional.of(new UserContext(userId, tenantId, "MANAGER", departmentId,
            "ACTIVE", "Teste"));
    }
}
