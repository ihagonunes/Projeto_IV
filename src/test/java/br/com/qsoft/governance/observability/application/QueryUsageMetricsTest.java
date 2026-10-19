package br.com.qsoft.governance.observability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueryUsageMetricsTest {

    private DashboardScopeResolver scopeResolver;
    private UsageMetricsRepository metricsRepository;
    private QueryUsageMetrics queryUsageMetrics;

    @BeforeEach
    void setUp() {
        scopeResolver = mock(DashboardScopeResolver.class);
        metricsRepository = mock(UsageMetricsRepository.class);
        queryUsageMetrics = new QueryUsageMetrics(scopeResolver, metricsRepository);
    }

    @Test
    void deve_gerarSeries_quando_granularidadeFornecida() {
        TenantId tenantId = TenantId.random();
        UserId userId = UserId.random();
        UUID deptId = UUID.randomUUID();
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T23:59:59Z");

        DashboardQueryCommand command = new DashboardQueryCommand(
            tenantId, userId, deptId, from, to, UsageMetricsRepository.Granularity.MONTH);

        UsageMetricsRepository.UsageMetricsFilter filter = new UsageMetricsRepository.UsageMetricsFilter(
            tenantId, null, java.util.Set.of(deptId), from, to,
            UsageMetricsRepository.Granularity.MONTH);

        when(scopeResolver.resolve(any(), any(), any(), any(), any(), any())).thenReturn(filter);
        when(metricsRepository.activeUsers(filter)).thenReturn(42L);

        List<UsageMetricsRepository.UsageSeriesPoint> seriesPoints = List.of(
            new UsageMetricsRepository.UsageSeriesPoint("2026-01-01T00:00:00Z", 10L)
        );
        when(metricsRepository.requestsByPeriod(filter)).thenReturn(seriesPoints);

        UsageMetricsSeries result = queryUsageMetrics.series(command);

        assertThat(result.granularity()).isEqualTo("MONTH");
        assertThat(result.from()).isEqualTo(from);
        assertThat(result.to()).isEqualTo(to);
        assertThat(result.activeUsers()).isEqualTo(42L);
        assertThat(result.series()).isEqualTo(seriesPoints);
    }

    @Test
    void deve_usarGranularidadePadraoDAY_quando_granularityNula() {
        TenantId tenantId = TenantId.random();
        UserId userId = UserId.random();
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T23:59:59Z");

        DashboardQueryCommand command = new DashboardQueryCommand(
            tenantId, userId, null, from, to, null);

        UsageMetricsRepository.UsageMetricsFilter filter = new UsageMetricsRepository.UsageMetricsFilter(
            tenantId, null, null, from, to, UsageMetricsRepository.Granularity.DAY);

        when(scopeResolver.resolve(any(), any(), any(), any(), any(), any())).thenReturn(filter);
        when(metricsRepository.activeUsers(filter)).thenReturn(10L);
        when(metricsRepository.requestsByPeriod(filter)).thenReturn(List.of());

        UsageMetricsSeries result = queryUsageMetrics.series(command);

        assertThat(result.granularity()).isEqualTo("DAY");
        assertThat(result.activeUsers()).isEqualTo(10L);
        assertThat(result.series()).isEmpty();
    }
}
