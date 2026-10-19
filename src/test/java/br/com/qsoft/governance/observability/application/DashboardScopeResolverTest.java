package br.com.qsoft.governance.observability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * O escopo do dashboard vem das CONCESSÕES ({@code visibilityFor}), nunca do
 * rótulo organizacional (ADR-36 item 9).
 */
class DashboardScopeResolverTest {

    private AuthorizationQuery authorizationQuery;
    private DashboardScopeResolver resolver;

    private final TenantId tenantId = TenantId.random();
    private final UserId userId = UserId.random();

    @BeforeEach
    void setUp() {
        authorizationQuery = mock(AuthorizationQuery.class);
        resolver = new DashboardScopeResolver(authorizationQuery);
    }

    private UsageMetricsRepository.UsageMetricsFilter resolve(UUID requested) {
        return resolver.resolve(tenantId, userId, requested, null, null,
            UsageMetricsRepository.Granularity.DAY);
    }

    @Test
    void deve_lancarExcecao_quando_tenantIdNulo() {
        assertThatThrownBy(() -> resolver.resolve(null, userId, null, null, null, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("tenantId");
    }

    @Test
    void deve_lancarExcecao_quando_userIdNulo() {
        assertThatThrownBy(() -> resolver.resolve(tenantId, null, null, null, null, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("userId");
    }

    @Test
    void deve_resolverSelfOnly_quandoConcessaoSelf() {
        when(authorizationQuery.visibilityFor(tenantId, userId, Resource.USAGE, Action.READ))
            .thenReturn(new VisibilityScope.SelfOnly(userId));

        UsageMetricsRepository.UsageMetricsFilter filter = resolve(UUID.randomUUID());

        assertThat(filter.userId()).isEqualTo(userId.value());
        assertThat(filter.departmentIds()).isEmpty();
    }

    @Test
    void deve_resolverDepartments_pelaUniao_dasConcessoes_ignorandoOPedido() {
        UUID deptA = UUID.randomUUID();
        UUID deptB = UUID.randomUUID();
        when(authorizationQuery.visibilityFor(tenantId, userId, Resource.USAGE, Action.READ))
            .thenReturn(new VisibilityScope.Departments(Set.of(deptA, deptB), userId));

        UsageMetricsRepository.UsageMetricsFilter filter = resolve(UUID.randomUUID());

        assertThat(filter.userId()).isNull();
        assertThat(filter.departmentIds()).containsExactlyInAnyOrder(deptA, deptB);
    }

    @Test
    void deve_resolverTenantWide_usandoDepartamentoSolicitado() {
        UUID requested = UUID.randomUUID();
        when(authorizationQuery.visibilityFor(tenantId, userId, Resource.USAGE, Action.READ))
            .thenReturn(new VisibilityScope.TenantWide());

        UsageMetricsRepository.UsageMetricsFilter filter = resolve(requested);

        assertThat(filter.userId()).isNull();
        assertThat(filter.departmentIds()).containsExactly(requested);
    }

    @Test
    void deve_resolverTenantWide_semFiltro_quandoNadaSolicitado() {
        when(authorizationQuery.visibilityFor(tenantId, userId, Resource.USAGE, Action.READ))
            .thenReturn(new VisibilityScope.TenantWide());

        UsageMetricsRepository.UsageMetricsFilter filter = resolve(null);

        assertThat(filter.userId()).isNull();
        assertThat(filter.departmentIds()).isEmpty();
    }

    @Test
    void deve_negar_quandoVisibilityNone() {
        when(authorizationQuery.visibilityFor(tenantId, userId, Resource.USAGE, Action.READ))
            .thenReturn(new VisibilityScope.None());

        assertThatThrownBy(() -> resolve(null))
            .isInstanceOf(UsageVisibilityDeniedException.class);
    }

    @Test
    void deve_preservarPeriodoEGranularidade() {
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();
        when(authorizationQuery.visibilityFor(tenantId, userId, Resource.USAGE, Action.READ))
            .thenReturn(new VisibilityScope.TenantWide());

        UsageMetricsRepository.UsageMetricsFilter filter = resolver.resolve(
            tenantId, userId, null, from, to, UsageMetricsRepository.Granularity.MONTH);

        assertThat(filter.tenantId()).isEqualTo(tenantId);
        assertThat(filter.from()).isEqualTo(from);
        assertThat(filter.to()).isEqualTo(to);
        assertThat(filter.granularity()).isEqualTo(UsageMetricsRepository.Granularity.MONTH);
    }
}
