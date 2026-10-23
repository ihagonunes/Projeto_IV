package br.com.qsoft.governance.observability.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.api.query.HistoricalIdentityQuery;
import br.com.qsoft.governance.department.api.query.HistoricalDepartmentQuery;
import br.com.qsoft.governance.observability.application.port.HistoricalDashboardQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.*;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QueryHistoricalDashboardTest {
    private final AuthorizationQuery auth = mock(AuthorizationQuery.class);
    private final HistoricalDashboardQuery query = mock(HistoricalDashboardQuery.class);
    private final HistoricalIdentityQuery identities = mock(HistoricalIdentityQuery.class);
    private final HistoricalDepartmentQuery departments = mock(HistoricalDepartmentQuery.class);
    private final DashboardScopeResolver resolver = mock(DashboardScopeResolver.class);
    private final QueryHistoricalDashboard service = new QueryHistoricalDashboard(auth, query, identities, departments, resolver);
    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UserId user = UserId.of(UUID.randomUUID());

    @Test
    void restrictedScopesNeverQueryHistoricalData() {
        for (var scope : new VisibilityScope[]{new VisibilityScope.None(),
                new VisibilityScope.SelfOnly(user), new VisibilityScope.Departments(Set.of(UUID.randomUUID()), user)}) {
            when(auth.visibilityFor(tenant, user, Resource.USAGE, Action.READ)).thenReturn(scope);
            assertThatThrownBy(() -> service.execute(tenant, user, null, null, null, null))
                .isInstanceOf(UsageVisibilityDeniedException.class);
        }
        verifyNoInteractions(query);
    }

    @Test
    void defaultPeriodUsesLatestHistoricalDateAndKeepsTextualFilters() {
        when(auth.visibilityFor(tenant, user, Resource.USAGE, Action.READ)).thenReturn(new VisibilityScope.TenantWide());
        when(query.coverage(any(HistoricalDashboardQuery.Filter.class))).thenReturn(new HistoricalDashboardQuery.Coverage(LocalDate.parse("2025-06-01"), LocalDate.parse("2026-07-31")));
        var result = service.execute(tenant, user, null, null, "D-1", "M-1");
        assertThat(result.from()).isEqualTo("2026-07-01");
        assertThat(result.to()).isEqualTo("2026-07-31");
        assertThat(result.currency()).isEqualTo("USD");
        verify(query).costs(new HistoricalDashboardQuery.Filter(tenant, result.from(), result.to(), "D-1", "M-1"));
    }

    @Test
    void invalidIntervalDoesNotReadMetrics() {
        when(auth.visibilityFor(tenant, user, Resource.USAGE, Action.READ)).thenReturn(new VisibilityScope.TenantWide());
        when(query.coverage(any(HistoricalDashboardQuery.Filter.class))).thenReturn(new HistoricalDashboardQuery.Coverage(null, null));
        assertThatThrownBy(() -> service.execute(tenant, user, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-07-31"), null, null))
            .isInstanceOf(IllegalArgumentException.class);
        verify(query, never()).series(any());
    }

    @Test
    void personalHistoryAlwaysUsesTheActorsLegacyIdEvenWithTenantGrant() {
        when(auth.visibilityFor(tenant, user, Resource.USAGE, Action.READ)).thenReturn(new VisibilityScope.TenantWide());
        when(identities.employeeId(tenant, user)).thenReturn(java.util.Optional.of("E1"));
        when(query.coverage(any(HistoricalDashboardQuery.Filter.class))).thenReturn(new HistoricalDashboardQuery.Coverage(null, null));
        service.own(tenant, user, null, null, null);
        var captor = org.mockito.ArgumentCaptor.forClass(HistoricalDashboardQuery.Filter.class);
        verify(query).series(captor.capture());
        assertThat(captor.getValue().scope().tenantWide()).isFalse();
        assertThat(captor.getValue().scope().employeeId()).isEqualTo("E1");
        assertThat(captor.getValue().scope().departmentIds()).isEmpty();
    }

    @Test
    void departmentScopeRequiresExplicitMappingAndRejectsOtherDepartments() {
        UUID departmentId = UUID.randomUUID();
        when(auth.visibilityFor(tenant, user, Resource.USAGE, Action.READ)).thenReturn(new VisibilityScope.Departments(Set.of(departmentId), user));
        when(identities.employeeId(tenant, user)).thenReturn(java.util.Optional.of("E1"));
        when(departments.legacyIds(tenant, Set.of(departmentId))).thenReturn(Set.of("D1"));
        when(query.coverage(any(HistoricalDashboardQuery.Filter.class))).thenReturn(new HistoricalDashboardQuery.Coverage(null, null));
        service.execute(tenant, user, null, null, "D1", null);
        var captor = org.mockito.ArgumentCaptor.forClass(HistoricalDashboardQuery.Filter.class);
        verify(query).costs(captor.capture());
        assertThat(captor.getValue().scope().departmentIds()).containsExactly("D1");
        assertThatThrownBy(() -> service.execute(tenant, user, null, null, "D2", null)).isInstanceOf(UsageVisibilityDeniedException.class);
    }
}
