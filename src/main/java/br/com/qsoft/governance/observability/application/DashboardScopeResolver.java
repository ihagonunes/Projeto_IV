package br.com.qsoft.governance.observability.application;

import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolve o <b>escopo de visibilidade</b> do dashboard a partir das concessões
 * do usuário ({@link AuthorizationQuery#visibilityFor}, ADR-36).
 * <ul>
 *   <li><b>TenantWide</b> — todo o tenant; o departamento pedido é filtro opcional;</li>
 *   <li><b>Departments</b> — união dos departamentos das concessões; o pedido é ignorado;</li>
 *   <li><b>SelfOnly</b> — apenas os próprios registros;</li>
 *   <li><b>None</b> — fail-closed (403): sem concessão aplicável, nenhum dado.</li>
 * </ul>
 * O rótulo organizacional ({@code hierarchyLevel}) não é lido: não é fonte de
 * autorização (ADR-36 item 9).
 */
@Component
public class DashboardScopeResolver {

    private final AuthorizationQuery authorizationQuery;

    public DashboardScopeResolver(AuthorizationQuery authorizationQuery) {
        this.authorizationQuery = authorizationQuery;
    }

    public UsageMetricsRepository.UsageMetricsFilter resolve(
            TenantId tenantId, UserId userId, UUID requestedDepartmentId,
            Instant from, Instant to, UsageMetricsRepository.Granularity granularity) {

        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");

        VisibilityScope scope = authorizationQuery.visibilityFor(
            tenantId, userId, Resource.USAGE, Action.READ);

        UUID scopedUserId = null;
        Set<UUID> departmentIds = Set.of();

        if (scope instanceof VisibilityScope.TenantWide) {
            departmentIds = requestedDepartmentId == null
                ? Set.of()
                : Set.of(requestedDepartmentId);
        } else if (scope instanceof VisibilityScope.Departments departments) {
            departmentIds = departments.ids();
        } else if (scope instanceof VisibilityScope.SelfOnly selfOnly) {
            scopedUserId = selfOnly.self().value();
        } else {
            // None: sem concessão aplicável com USAGE:READ. Inatingível após o gate
            // BY_TARGET (que exige a mesma permissão), mas mantido como fail-closed.
            throw new UsageVisibilityDeniedException();
        }

        return new UsageMetricsRepository.UsageMetricsFilter(
            tenantId, scopedUserId, departmentIds, from, to, granularity);
    }
}
