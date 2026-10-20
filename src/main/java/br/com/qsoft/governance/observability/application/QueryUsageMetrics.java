package br.com.qsoft.governance.observability.application;

import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository;
import org.springframework.stereotype.Service;

/**
 * Série temporal de requisições e usuários ativos por período (SPI-88).
 * <p>
 * O escopo (SELF/DEPARTMENT/TENANT) é resolvido a partir do IAM (SPI-94) e o
 * período é validado/normalizado (SPI-90) — o frontend não decide nenhum dos
 * dois.
 */
@Service
public class QueryUsageMetrics {

    private final DashboardScopeResolver scopeResolver;
    private final UsageMetricsRepository metricsRepository;

    public QueryUsageMetrics(DashboardScopeResolver scopeResolver,
                             UsageMetricsRepository metricsRepository) {
        this.scopeResolver = scopeResolver;
        this.metricsRepository = metricsRepository;
    }

    public UsageMetricsSeries series(DashboardQueryCommand command) {
        DashboardPeriod.Window window = DashboardPeriod.resolve(command.from(), command.to());
        UsageMetricsRepository.Granularity granularity = command.granularity() == null
            ? UsageMetricsRepository.Granularity.DAY
            : command.granularity();

        UsageMetricsRepository.UsageMetricsFilter filter = scopeResolver.resolve(
            command.tenantId(), command.userId(), command.departmentId(),
            command.modelId(), command.targetUserId(), window.from(), window.to(), granularity);

        return new UsageMetricsSeries(
            granularity.name(),
            window.from(),
            window.to(),
            metricsRepository.activeUsers(filter),
            metricsRepository.requestsByPeriod(filter)
        );
    }
}
