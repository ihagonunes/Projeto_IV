package br.com.qsoft.governance.observability.application;

import br.com.qsoft.governance.department.api.model.BudgetLimitInfo;
import br.com.qsoft.governance.department.api.query.DepartmentBudgetQuery;
import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository;
import br.com.qsoft.governance.observability.domain.BudgetProjection;
import br.com.qsoft.governance.observability.domain.BudgetProjectionCalculator;
import br.com.qsoft.governance.observability.domain.BudgetProjectionFacts;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Projeção determinística do orçamento departamental (ADR-41/SPEC-09).
 * <p>
 * O custo conhecido vem dos {@code UsageRecord} do departamento, sempre com
 * {@code tenant_id} explícito no adaptador nativo; o orçamento vem de
 * {@link DepartmentBudgetQuery}. Nenhuma chamada a LLM participa do cálculo.
 */
@Service
public class ProjectDepartmentBudget {

    public static final int DEFAULT_WINDOW_DAYS = 14;
    public static final int MIN_WINDOW_DAYS = 7;
    public static final int MAX_WINDOW_DAYS = 60;

    private final DepartmentBudgetQuery departmentBudgetQuery;
    private final UsageMetricsRepository usageMetricsRepository;
    private final Clock clock;

    @Autowired
    public ProjectDepartmentBudget(DepartmentBudgetQuery departmentBudgetQuery,
                                   UsageMetricsRepository usageMetricsRepository) {
        this(departmentBudgetQuery, usageMetricsRepository, Clock.systemUTC());
    }

    ProjectDepartmentBudget(DepartmentBudgetQuery departmentBudgetQuery,
                            UsageMetricsRepository usageMetricsRepository, Clock clock) {
        this.departmentBudgetQuery = departmentBudgetQuery;
        this.usageMetricsRepository = usageMetricsRepository;
        this.clock = clock;
    }

    public BudgetProjection query(TenantId tenantId, UUID departmentId, YearMonth month,
                                  int windowDays, VisibilityScope visibility) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
        Objects.requireNonNull(departmentId, "departmentId é obrigatório.");
        Objects.requireNonNull(month, "month é obrigatório.");
        Objects.requireNonNull(visibility, "visibility é obrigatória.");
        validateWindow(windowDays);
        if (!visibility.coversDepartment(departmentId)) {
            throw new UsageVisibilityDeniedException();
        }

        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (month.isAfter(YearMonth.from(today))) {
            throw new IllegalArgumentException("mes futuro não possui projeção.");
        }
        BudgetLimitInfo budget = departmentBudgetQuery.budgetFor(tenantId, departmentId, month)
            .orElseThrow(ProjectionDepartmentNotFoundException::new);
        LocalDate monthCutoff = month.isBefore(YearMonth.from(today))
            ? month.atEndOfMonth() : today;

        BigDecimal monthCost = cost(tenantId, departmentId, visibility,
            month.atDay(1), monthCutoff);
        // A janela da média termina sempre em hoje (SPEC-09 §2); no mês
        // passado o realizado continua sendo o mês inteiro, mas a média é a
        // janela móvel corrente.
        BigDecimal windowCost = cost(tenantId, departmentId, visibility,
            today.minusDays(windowDays - 1L), today);

        return BudgetProjectionCalculator.calculate(new BudgetProjectionFacts(
            month, today, windowDays, budget.amount(), budget.fromDefault(),
            budget.alertThresholdPct(), monthCost, windowCost));
    }

    private BigDecimal cost(TenantId tenantId, UUID departmentId, VisibilityScope visibility,
                            LocalDate from, LocalDate toInclusive) {
        Instant fromInstant = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toInstant = toInclusive.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return usageMetricsRepository.costByDepartment(
                new UsageMetricsRepository.UsageMetricsFilter(
                    tenantId, visibility, departmentId, fromInstant, toInstant,
                    UsageMetricsRepository.Granularity.DAY))
            .stream()
            .map(UsageMetricsRepository.DepartmentCost::estimatedCost)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void validateWindow(int windowDays) {
        if (windowDays < MIN_WINDOW_DAYS || windowDays > MAX_WINDOW_DAYS) {
            throw new IllegalArgumentException(
                "janelaDias deve estar entre " + MIN_WINDOW_DAYS + " e " + MAX_WINDOW_DAYS + ".");
        }
    }
}
