package br.com.qsoft.governance.observability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.department.api.model.BudgetLimitInfo;
import br.com.qsoft.governance.department.api.query.DepartmentBudgetQuery;
import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository;
import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository.DepartmentCost;
import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository.UsageMetricsFilter;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Cálculo do status do orçamento (SPEC-02 §3/§6). */
class QueryBudgetStatusTest {

    private final TenantId tenantId = TenantId.random();
    private final YearMonth month = YearMonth.of(2026, 10);
    private final UUID departmentId = UUID.randomUUID();
    private final DepartmentBudgetQuery budgets = mock(DepartmentBudgetQuery.class);
    private final UsageMetricsRepository metrics = mock(UsageMetricsRepository.class);
    private final QueryBudgetStatus useCase = new QueryBudgetStatus(budgets, metrics);

    private BudgetLimitInfo budget(BigDecimal amount, int threshold) {
        return new BudgetLimitInfo(departmentId, "Marketing", month, amount, threshold, false);
    }

    private void givenCost(BigDecimal cost) {
        when(metrics.costByDepartment(any())).thenReturn(cost == null
            ? List.of()
            : List.of(new DepartmentCost(departmentId, 1, 10, 20, cost)));
    }

    private BudgetStatusItem calculate(BigDecimal amount, int threshold, BigDecimal cost) {
        when(budgets.budgetsFor(tenantId, month)).thenReturn(List.of(budget(amount, threshold)));
        givenCost(cost);
        return useCase.query(tenantId, month, new VisibilityScope.TenantWide()).items().get(0);
    }

    @Test
    void aggregateWithoutKnownCostCountsAsZero() {
        when(budgets.budgetsFor(tenantId, month)).thenReturn(List.of(budget(BigDecimal.TEN, 80)));
        when(metrics.costByDepartment(any())).thenReturn(List.of(new DepartmentCost(departmentId, 1, 10, 20, null)));

        var item = useCase.query(tenantId, month, new VisibilityScope.TenantWide()).items().getFirst();

        assertThat(item).isEqualTo(calculate(BigDecimal.TEN, 80, BigDecimal.ZERO));
    }

    @Test
    void abaixoDeOitentaPorCentoEhOk() {
        BudgetStatusItem item = calculate(new BigDecimal("1000.00"), 80, new BigDecimal("799.90"));
        assertThat(item.pctUsed()).isEqualByComparingTo("79.99");
        assertThat(item.status()).isEqualTo(BudgetStatus.OK);
    }

    @Test
    void oitentaPorCentoEhAlerta() {
        BudgetStatusItem item = calculate(new BigDecimal("1000.00"), 80, new BigDecimal("800.00"));
        assertThat(item.pctUsed()).isEqualByComparingTo("80.00");
        assertThat(item.status()).isEqualTo(BudgetStatus.ALERTA);
    }

    @Test
    void noventaENoveVirgulaNoveNoveEhAlerta() {
        BudgetStatusItem item = calculate(new BigDecimal("1000.00"), 80, new BigDecimal("999.90"));
        assertThat(item.pctUsed()).isEqualByComparingTo("99.99");
        assertThat(item.status()).isEqualTo(BudgetStatus.ALERTA);
    }

    @Test
    void cemPorCentoEhExcedido() {
        BudgetStatusItem item = calculate(new BigDecimal("1000.00"), 80, new BigDecimal("1000.00"));
        assertThat(item.pctUsed()).isEqualByComparingTo("100.00");
        assertThat(item.status()).isEqualTo(BudgetStatus.EXCEDIDO);
    }

    @Test
    void limiarCustomizadoDeCinquentaPorCento() {
        BudgetStatusItem item = calculate(new BigDecimal("1000.00"), 50, new BigDecimal("500.00"));
        assertThat(item.status()).isEqualTo(BudgetStatus.ALERTA);
    }

    @Test
    void orcamentoZeroSemCustoEhOk() {
        BudgetStatusItem item = calculate(BigDecimal.ZERO, 80, null);
        assertThat(item.actualCost()).isEqualByComparingTo("0");
        assertThat(item.pctUsed()).isNull();
        assertThat(item.status()).isEqualTo(BudgetStatus.OK);
    }

    @Test
    void orcamentoZeroComCustoEhExcedido() {
        BudgetStatusItem item = calculate(BigDecimal.ZERO, 80, new BigDecimal("0.01"));
        assertThat(item.pctUsed()).isNull();
        assertThat(item.status()).isEqualTo(BudgetStatus.EXCEDIDO);
    }

    @Test
    void semOrcamentoNaoCalculaPercentual() {
        BudgetStatusItem item = calculate(null, 80, new BigDecimal("12.34"));
        assertThat(item.budget()).isNull();
        assertThat(item.pctUsed()).isNull();
        assertThat(item.status()).isEqualTo(BudgetStatus.SEM_ORCAMENTO);
    }

    @Test
    void percentualArredondaParaDuasCasasHalfUp() {
        BudgetStatusItem item = calculate(new BigDecimal("3.00"), 80, BigDecimal.ONE);
        assertThat(item.pctUsed()).isEqualByComparingTo("33.33");
        assertThat(item.status()).isEqualTo(BudgetStatus.OK);
    }

    @Test
    void custoEhBuscadoNoIntervaloDoMesEmUtc() {
        calculate(new BigDecimal("1000.00"), 80, BigDecimal.ZERO);
        ArgumentCaptor<UsageMetricsFilter> captor = ArgumentCaptor.forClass(UsageMetricsFilter.class);
        org.mockito.Mockito.verify(metrics).costByDepartment(captor.capture());
        assertThat(captor.getValue().from()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(captor.getValue().to()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
        assertThat(captor.getValue().tenantId()).isEqualTo(tenantId);
    }

    @Test
    void selfOnlyEhNegado() {
        assertThatThrownBy(() -> useCase.query(tenantId, month,
            new VisibilityScope.SelfOnly(UserId.random())))
            .isInstanceOf(UsageVisibilityDeniedException.class);
    }

    @Test
    void departmentsMostraSomenteOsDepartamentosCobertos() {
        UUID outro = UUID.randomUUID();
        when(budgets.budgetsFor(tenantId, month)).thenReturn(List.of(
            budget(new BigDecimal("100.00"), 80),
            new BudgetLimitInfo(outro, "Outro", month, new BigDecimal("100.00"), 80, false)));
        givenCost(BigDecimal.TEN);

        BudgetStatusReport report = useCase.query(tenantId, month,
            new VisibilityScope.Departments(Set.of(departmentId), UserId.random()));

        assertThat(report.items()).extracting(BudgetStatusItem::departmentId)
            .containsExactly(departmentId);
    }
}
