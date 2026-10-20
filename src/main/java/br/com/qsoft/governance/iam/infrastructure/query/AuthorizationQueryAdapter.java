package br.com.qsoft.governance.iam.infrastructure.query;

import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.application.rbac.PolicyEvaluator;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.AccessTarget;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import org.springframework.stereotype.Component;

/**
 * Adapter que satisfaz o contrato público {@link AuthorizationQuery} delegando
 * ao {@link PolicyEvaluator} (ADR-36).
 */
@Component
public class AuthorizationQueryAdapter implements AuthorizationQuery {

    private final PolicyEvaluator policyEvaluator;

    public AuthorizationQueryAdapter(PolicyEvaluator policyEvaluator) {
        this.policyEvaluator = policyEvaluator;
    }

    @Override
    public boolean isAllowed(TenantId tenantId, UserId actor, Resource resource, Action action,
                             AccessTarget target) {
        return policyEvaluator.isAllowed(tenantId, actor, resource, action, target);
    }

    @Override
    public VisibilityScope visibilityFor(TenantId tenantId, UserId actor, Resource resource, Action action) {
        return policyEvaluator.visibilityFor(tenantId, actor, resource, action);
    }
}
