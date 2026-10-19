package br.com.qsoft.governance.iam.infrastructure.query;

import br.com.qsoft.governance.iam.api.model.AuthorizationResult;
import br.com.qsoft.governance.iam.api.model.AuthorizationScope;
import br.com.qsoft.governance.iam.api.model.Permission;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.application.rbac.PolicyEvaluator;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.AccessTarget;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import org.springframework.stereotype.Component;

/**
 * Adapter que satisfaz o contrato público {@link AuthorizationQuery} do IAM.
 * As decisões com escopo delegam ao {@link PolicyEvaluator} (ADR-36). Os
 * métodos legados continuam disponíveis (até a remoção em S3-A8).
 */
@Component
public class AuthorizationQueryAdapter implements AuthorizationQuery {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PolicyEvaluator policyEvaluator;

    public AuthorizationQueryAdapter(UserRepository userRepository,
                                     TenantRepository tenantRepository,
                                     PolicyEvaluator policyEvaluator) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
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

    @Override
    @Deprecated(forRemoval = true)
    public AuthorizationResult checkPermission(UserId userId, Permission permission) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return AuthorizationResult.deny("Usuário não encontrado.");
        }
        if (user.status() != UserStatus.ACTIVE) {
            return AuthorizationResult.deny("Usuário inativo.");
        }
        var tenant = tenantRepository.findById(user.tenantId()).orElse(null);
        if (tenant == null || tenant.status() != TenantStatus.ACTIVE) {
            return AuthorizationResult.deny("Tenant inativo.");
        }
        if (!user.hasPermission(permission)) {
            return AuthorizationResult.deny("Permissão negada: " + permission.name() + ".");
        }
        return AuthorizationResult.allow();
    }

    @Override
    @Deprecated(forRemoval = true)
    public AuthorizationScope resolveScope(UserId userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return AuthorizationScope.SELF;
        }
        if (user.isAdmin()) {
            return AuthorizationScope.TENANT;
        }
        return switch (user.hierarchyLevel()) {
            case EXECUTIVE -> AuthorizationScope.TENANT;
            case MANAGER, LEADER -> AuthorizationScope.DEPARTMENT;
            case COLLABORATOR -> AuthorizationScope.SELF;
        };
    }
}
