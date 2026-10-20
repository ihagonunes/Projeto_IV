package br.com.qsoft.governance.observability.application;

import br.com.qsoft.governance.department.api.model.BudgetLimitInfo;
import br.com.qsoft.governance.department.api.query.DepartmentBudgetQuery;
import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Calcula o status do orçamento por departamento no mês (ADR-06/SPEC-02 §3).
 * <p>
 * O custo real vem dos {@code UsageRecord} (valores {@code INCONCLUSIVE} nunca
 * chegam aqui). Alertas <b>não</b> são persistidos: o status é derivado na
 * leitura. Meses são limites em UTC ({@code [início, início do mês seguinte)}).
 */
@Service
public class QueryBudgetStatus {

    private final DepartmentBudgetQuery departmentBudgetQuery;
    private final UsageMetricsRepository usageMetricsRepository;

    public QueryBudgetStatus(DepartmentBudgetQuery departmentBudgetQuery,
                             UsageMetricsRepository usageMetricsRepository) {
        this.departmentBudgetQuery = departmentBudgetQuery;
        this.usageMetricsRepository = usageMetricsRepository;
    }

    public BudgetStatusReport query(TenantId tenantId, YearMonth month,
                                    VisibilityScope visibility) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(visibility, "visibility");
        if (!(visibility instanceof VisibilityScope.TenantWide
            || visibility instanceof VisibilityScope.Departments)) {
            throw new UsageVisibilityDeniedException();
        }

        Instant from = month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        // Limite superior exclusivo: [início do mês, início do mês seguinte).
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Map<UUID, BigDecimal> costs = usageMetricsRepository
            .costByDepartment(new UsageMetricsRepository.UsageMetricsFilter(
                tenantId, visibility, null, from, to,
                UsageMetricsRepository.Granularity.MONTH))
            .stream()
            .collect(java.util.stream.Collectors.toMap(
                UsageMetricsRepository.DepartmentCost::departmentId,
                cost -> cost.estimatedCost() == null ? BigDecimal.ZERO : cost.estimatedCost(),
                BigDecimal::add));

        List<BudgetStatusItem> items = departmentBudgetQuery.budgetsFor(tenantId, month).stream()
            .filter(budget -> visibility.coversDepartment(budget.departmentId()))
            .map(budget -> item(budget, costs.getOrDefault(budget.departmentId(), BigDecimal.ZERO)))
            .toList();
        return new BudgetStatusReport(month, items);
    }

    private BudgetStatusItem item(BudgetLimitInfo budget, BigDecimal actualCost) {
        BigDecimal amount = budget.amount();
        BigDecimal pct = amount == null || amount.signum() == 0
            ? null
            : actualCost.multiply(BigDecimal.valueOf(100))
                .divide(amount, 2, RoundingMode.HALF_UP);
        return new BudgetStatusItem(budget.departmentId(), budget.departmentName(), amount,
            actualCost, pct, budget.alertThresholdPct(), status(amount, actualCost, pct,
            budget.alertThresholdPct()), budget.fromDefault());
    }

    private static BudgetStatus status(BigDecimal amount, BigDecimal actualCost, BigDecimal pct,
                                       int thresholdPct) {
        if (amount == null) {
            return BudgetStatus.SEM_ORCAMENTO;
        }
        if (amount.signum() == 0) {
            return actualCost.signum() == 0 ? BudgetStatus.OK : BudgetStatus.EXCEDIDO;
        }
        if (pct.compareTo(BigDecimal.valueOf(100)) >= 0) {
            return BudgetStatus.EXCEDIDO;
        }
        return pct.compareTo(BigDecimal.valueOf(thresholdPct)) >= 0
            ? BudgetStatus.ALERTA
            : BudgetStatus.OK;
    }
}
