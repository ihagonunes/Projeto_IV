package br.com.qsoft.governance.observability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueryCostMetricsTest {

    private DashboardScopeResolver scopeResolver;
    private UsageMetricsRepository metricsRepository;
    private QueryCostMetrics queryCostMetrics;

    @BeforeEach
    void setUp() {
        scopeResolver = mock(DashboardScopeResolver.class);
        metricsRepository = mock(UsageMetricsRepository.class);
        queryCostMetrics = new QueryCostMetrics(scopeResolver, metricsRepository);
    }

    @Test
    void deve_retornarCustosPorDepartamento() {
        TenantId tenantId = TenantId.random();
        UserId userId = UserId.random();
        UUID deptId = UUID.randomUUID();
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T23:59:59Z");

        DashboardQueryCommand command = new DashboardQueryCommand(
            tenantId, userId, deptId, from, to, UsageMetricsRepository.Granularity.DAY);

        UsageMetricsRepository.UsageMetricsFilter filter = new UsageMetricsRepository.UsageMetricsFilter(
            tenantId, new br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope.TenantWide(), deptId, from, to,
            UsageMetricsRepository.Granularity.DAY);

        when(scopeResolver.resolve(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(filter);

        List<UsageMetricsRepository.CostGroup> groups = List.of(
            new UsageMetricsRepository.CostGroup(
                deptId, 100L, 1000L, 2000L, new BigDecimal("15.50"))
        );
        when(metricsRepository.costGroups(filter, UsageMetricsRepository.CostGroupBy.DEPARTMENT))
            .thenReturn(groups);

        List<UsageMetricsRepository.CostGroup> result = queryCostMetrics.byGroup(
            command, UsageMetricsRepository.CostGroupBy.DEPARTMENT);

        assertThat(result).isEqualTo(groups);
    }
}
