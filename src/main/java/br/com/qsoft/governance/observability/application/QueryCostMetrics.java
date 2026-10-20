package br.com.qsoft.governance.observability.application;

import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Tokens e custo agregados por departamento (SPI-89). O escopo do usuário
 * (SPI-94) restringe o resultado: um gestor vê apenas o próprio departamento.
 */
@Service
public class QueryCostMetrics {

    private final DashboardScopeResolver scopeResolver;
    private final UsageMetricsRepository metricsRepository;

    public QueryCostMetrics(DashboardScopeResolver scopeResolver,
                            UsageMetricsRepository metricsRepository) {
        this.scopeResolver = scopeResolver;
        this.metricsRepository = metricsRepository;
    }

    public List<UsageMetricsRepository.CostGroup> byGroup(
            DashboardQueryCommand command, UsageMetricsRepository.CostGroupBy groupBy) {
        DashboardPeriod.Window window = DashboardPeriod.resolve(command.from(), command.to());
        UsageMetricsRepository.UsageMetricsFilter filter = scopeResolver.resolve(
            command.tenantId(), command.userId(), command.departmentId(),
            command.modelId(), command.targetUserId(), window.from(), window.to(),
            UsageMetricsRepository.Granularity.DAY);
        return metricsRepository.costGroups(filter, groupBy);
    }
}
