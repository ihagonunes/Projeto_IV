package br.com.qsoft.governance.observability.application;

import br.com.qsoft.governance.department.api.query.HistoricalDepartmentQuery;
import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.api.query.HistoricalIdentityQuery;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.observability.application.port.PeerConsumptionQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import br.com.qsoft.governance.observability.application.port.PeerConsumptionQuery.Average;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class QueryPeerConsumption {
    public enum Source { HISTORICO, OPERACIONAL }

    private final AuthorizationQuery authorization;
    private final IdentityQuery identities;
    private final HistoricalIdentityQuery legacyIdentities;
    private final HistoricalDepartmentQuery legacyDepartments;
    private final PeerConsumptionQuery query;

    public QueryPeerConsumption(AuthorizationQuery authorization, IdentityQuery identities,
                                HistoricalIdentityQuery legacyIdentities, HistoricalDepartmentQuery legacyDepartments,
                                PeerConsumptionQuery query) {
        this.authorization = authorization;
        this.identities = identities;
        this.legacyIdentities = legacyIdentities;
        this.legacyDepartments = legacyDepartments;
        this.query = query;
    }

    public Average average(TenantId tenant, UserId actor, YearMonth month, Source source) {
        if (authorization.visibilityFor(tenant, actor, Resource.USAGE, Action.READ) instanceof VisibilityScope.None) {
            throw new UsageVisibilityDeniedException();
        }
        if (!month.isBefore(YearMonth.now(ZoneOffset.UTC))) return null;

        var eligible = identities.eligibleUsers(tenant);
        var department = eligible.stream().filter(user -> actor.equals(user.userId())).findFirst()
            .map(UserContext::departmentId).orElse(null);
        if (department == null) return null;

        Set<UserId> peers = eligible.stream()
            .filter(user -> tenant.equals(user.tenantId()) && department.equals(user.departmentId()))
            .map(UserContext::userId).filter(id -> !actor.equals(id)).collect(Collectors.toSet());
        if (peers.size() < 10) return null;

        if (source == Source.OPERACIONAL) {
            return query.operational(tenant, department, peers.stream().map(UserId::value).toList(), month);
        }
        var departments = legacyDepartments.legacyIds(tenant, Set.of(department));
        if (departments.isEmpty()) return null;
        var ownLegacyId = legacyIdentities.employeeId(tenant, actor).orElse(null);
        if (ownLegacyId == null) return null;
        var legacyPeers = legacyIdentities.employeeIds(tenant, peers).values().stream()
            .filter(id -> !ownLegacyId.equals(id)).collect(Collectors.toSet());
        return legacyPeers.size() < 10 ? null : query.historical(tenant, departments, legacyPeers, month);
    }
}
