package br.com.qsoft.governance.observability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

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
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class QueryPeerConsumptionTest {
    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UserId actor = UserId.of(UUID.randomUUID());
    private final UUID department = UUID.randomUUID();
    private final YearMonth month = YearMonth.of(2026, 7);
    private final AuthorizationQuery authorization = mock(AuthorizationQuery.class);
    private final IdentityQuery identity = mock(IdentityQuery.class);
    private final PeerConsumptionQuery port = mock(PeerConsumptionQuery.class);
    private final QueryPeerConsumption query = new QueryPeerConsumption(authorization, identity,
        mock(HistoricalIdentityQuery.class), mock(HistoricalDepartmentQuery.class), port);

    @Test
    void onlyOwnDepartmentAndTenantReachAggregateAndActorIsExcluded() {
        when(authorization.visibilityFor(tenant, actor, Resource.USAGE, Action.READ))
            .thenReturn(new VisibilityScope.SelfOnly(actor));
        var peers = IntStream.range(0, 10)
            .mapToObj(i -> user(UserId.of(UUID.randomUUID()), tenant, department)).toList();
        var contexts = new ArrayList<>(peers);
        contexts.add(user(actor, tenant, department));
        contexts.add(user(UserId.of(UUID.randomUUID()), tenant, UUID.randomUUID()));
        contexts.add(user(UserId.of(UUID.randomUUID()), TenantId.of(UUID.randomUUID()), department));
        var ids = peers.stream().map(user -> user.userId().value()).collect(Collectors.toSet());
        when(identity.eligibleUsers(tenant)).thenReturn(contexts);
        when(port.operational(eq(tenant), eq(department), argThat(actual -> Set.copyOf(actual).equals(ids)),
            eq(month))).thenReturn(new PeerConsumptionQuery.Average(BigDecimal.TEN, BigDecimal.ONE));
        assertThat(query.average(tenant, actor, month, QueryPeerConsumption.Source.OPERACIONAL))
            .isEqualTo(new PeerConsumptionQuery.Average(BigDecimal.TEN, BigDecimal.ONE));
    }

    @Test
    void smallCohortNeverReachesAggregateAndMissingPermissionIsDenied() {
        when(authorization.visibilityFor(tenant, actor, Resource.USAGE, Action.READ))
            .thenReturn(new VisibilityScope.SelfOnly(actor), new VisibilityScope.None());
        when(identity.eligibleUsers(tenant)).thenReturn(java.util.List.of(user(actor, tenant, department)));
        assertThat(query.average(tenant, actor, month, QueryPeerConsumption.Source.HISTORICO)).isNull();
        assertThatThrownBy(() -> query.average(tenant, actor, month, QueryPeerConsumption.Source.HISTORICO))
            .isInstanceOf(UsageVisibilityDeniedException.class);
        verifyNoInteractions(port);
    }

    private static UserContext user(UserId user, TenantId tenant, UUID department) {
        return new UserContext(user, tenant, "COLLABORATOR", department, "ACTIVE");
    }
}
