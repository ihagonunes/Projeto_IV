package br.com.qsoft.governance.observability.application;

import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.api.query.HistoricalIdentityQuery;
import br.com.qsoft.governance.department.api.query.HistoricalDepartmentQuery;
import br.com.qsoft.governance.observability.application.port.HistoricalDashboardQuery;
import br.com.qsoft.governance.observability.application.port.HistoricalDashboardQuery.Dashboard;
import br.com.qsoft.governance.observability.application.port.HistoricalDashboardQuery.Filter;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueryHistoricalDashboard {
    private final AuthorizationQuery authorization;
    private final HistoricalDashboardQuery query;
    private final HistoricalIdentityQuery identities;
    private final HistoricalDepartmentQuery departments;
    private final DashboardScopeResolver scopeResolver;

    public QueryHistoricalDashboard(AuthorizationQuery authorization, HistoricalDashboardQuery query,
                                    HistoricalIdentityQuery identities, HistoricalDepartmentQuery departments,
                                    DashboardScopeResolver scopeResolver) {
        this.authorization = authorization;
        this.query = query;
        this.identities = identities;
        this.departments = departments;
        this.scopeResolver = scopeResolver;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Dashboard execute(TenantId tenant, UserId actor, LocalDate from, LocalDate to,
                             String departmentId, String modelId) {
        return read(tenant, actor, from, to, departmentId, modelId, false, null);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Dashboard selected(TenantId tenant, UserId actor, LocalDate from, LocalDate to,
                               String departmentId, String modelId, UUID requestedUser) {
        return read(tenant, actor, from, to, departmentId, modelId, false, requestedUser);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Dashboard own(TenantId tenant, UserId actor, LocalDate from, LocalDate to, String modelId) {
        return read(tenant, actor, from, to, null, modelId, true, null);
    }

    private Dashboard read(TenantId tenant, UserId actor, LocalDate from, LocalDate to,
                           String departmentId, String modelId, boolean own, UUID requestedUser) {
        var visibility = authorization.visibilityFor(tenant, actor, Resource.USAGE, Action.READ);
        if (visibility instanceof VisibilityScope.None) throw new UsageVisibilityDeniedException();
        HistoricalDashboardQuery.Scope scope;
        if (!own && visibility instanceof VisibilityScope.TenantWide) {
            scope = HistoricalDashboardQuery.Scope.tenant();
        } else {
            String employee = identities.employeeId(tenant, actor).orElse(null);
            Set<String> legacyDepartments = !own && visibility instanceof VisibilityScope.Departments d
                ? departments.legacyIds(tenant, d.ids()) : Set.of();
            if (employee == null && legacyDepartments.isEmpty()) throw new UsageVisibilityDeniedException();
            scope = new HistoricalDashboardQuery.Scope(false, legacyDepartments, employee);
        }
        if (departmentId != null && !scope.tenantWide() && !scope.departmentIds().contains(departmentId)) {
            throw new UsageVisibilityDeniedException();
        }
        String selectedEmployee = null;
        if (requestedUser != null) {
            scopeResolver.resolve(tenant, actor, null, null, requestedUser, Instant.EPOCH, Instant.now(),
                br.com.qsoft.governance.observability.application.port.UsageMetricsRepository.Granularity.DAY);
            selectedEmployee = identities.employeeId(tenant, UserId.of(requestedUser)).orElseThrow(UsageVisibilityDeniedException::new);
        }
        var metadata = new Filter(tenant, null, null, null, null, scope);
        var coverage = query.coverage(metadata);
        LocalDate end = to == null ? (coverage.to() == null ? LocalDate.now(java.time.ZoneOffset.UTC) : coverage.to()) : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Data inicial deve ser anterior ou igual à final.");
        }
        var filter = new Filter(tenant, start, end, departmentId, modelId, scope, selectedEmployee);
        return new Dashboard("HISTORICAL", "USD", start, end, coverage,
            query.activeUsers(filter), query.departments(metadata), query.models(metadata),
            query.series(filter), query.costs(filter));
    }
}
