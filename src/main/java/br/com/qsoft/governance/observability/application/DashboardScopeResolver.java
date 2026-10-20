package br.com.qsoft.governance.observability.application;

import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolve o escopo de visibilidade do dashboard a partir das concessões do
 * usuário ({@link AuthorizationQuery#visibilityFor}, ADR-36).
 * <p>
 * Filtro pedido fora do escopo → 403 ({@link UsageVisibilityDeniedException}).
 * O rótulo organizacional não é lido: não é fonte de autorização (ADR-36 item 9).
 */
@Component
public class DashboardScopeResolver {

    private final AuthorizationQuery authorizationQuery;
    private final IdentityQuery identityQuery;

    public DashboardScopeResolver(AuthorizationQuery authorizationQuery,
                                  IdentityQuery identityQuery) {
        this.authorizationQuery = authorizationQuery;
        this.identityQuery = identityQuery;
    }

    /** Compatibilidade com os consumidores anteriores aos filtros de modelo/usuário. */
    public UsageMetricsRepository.UsageMetricsFilter resolve(
            TenantId tenantId, UserId userId, UUID requestedDepartmentId,
            Instant from, Instant to, UsageMetricsRepository.Granularity granularity) {
        return resolve(tenantId, userId, requestedDepartmentId, null, null, from, to, granularity);
    }

    public UsageMetricsRepository.UsageMetricsFilter resolve(
            TenantId tenantId, UserId userId, UUID requestedDepartmentId,
            UUID requestedModelId, UUID requestedUserId, Instant from, Instant to,
            UsageMetricsRepository.Granularity granularity) {

        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");

        VisibilityScope visibility = authorizationQuery.visibilityFor(
            tenantId, userId, Resource.USAGE, Action.READ);

        if (visibility instanceof VisibilityScope.None) {
            // Sem concessão aplicável: 403 antes de qualquer query.
            throw new UsageVisibilityDeniedException();
        }
        if (requestedDepartmentId != null && !coversDepartment(visibility, requestedDepartmentId)) {
            throw new UsageVisibilityDeniedException();
        }
        // Filtro de usuário fora do escopo → 403 (SPI-113). O tenant do alvo
        // vem do IAM (IdentityQuery), nunca do cliente.
        if (requestedUserId != null
            && !coversUser(visibility, tenantId, requestedUserId)) {
            throw new UsageVisibilityDeniedException();
        }

        return new UsageMetricsRepository.UsageMetricsFilter(
            tenantId, visibility, requestedDepartmentId, requestedModelId, requestedUserId,
            from, to, granularity);
    }

    private static boolean coversDepartment(VisibilityScope visibility, UUID departmentId) {
        return visibility instanceof VisibilityScope.TenantWide
            || (visibility instanceof VisibilityScope.Departments departments
                && departments.ids().contains(departmentId));
    }

    private boolean coversUser(VisibilityScope visibility, TenantId tenantId, UUID requestedUserId) {
        if (visibility instanceof VisibilityScope.SelfOnly selfOnly) {
            return selfOnly.self().value().equals(requestedUserId);
        }

        Optional<UserContext> target = identityQuery.getUserContext(UserId.of(requestedUserId))
            .filter(context -> context.tenantId().equals(tenantId));
        if (target.isEmpty()) {
            return false;
        }
        if (visibility instanceof VisibilityScope.TenantWide) {
            return true;
        }
        if (visibility instanceof VisibilityScope.Departments departments) {
            if (departments.self().value().equals(requestedUserId)) {
                return true;
            }
            UUID departmentId = target.get().departmentId();
            return departmentId != null && departments.ids().contains(departmentId);
        }
        return false;
    }
}
