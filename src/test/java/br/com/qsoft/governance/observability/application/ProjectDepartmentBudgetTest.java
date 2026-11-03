package br.com.qsoft.governance.observability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.department.api.model.BudgetLimitInfo;
import br.com.qsoft.governance.department.api.query.DepartmentBudgetQuery;
import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository;
import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository.DepartmentCost;
import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository.UsageMetricsFilter;
import br.com.qsoft.governance.observability.domain.BudgetProjection;
import br.com.qsoft.governance.observability.domain.BudgetRecommendation;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** PJ01/PJ05/PJ08 e autorização fina do caso de uso (SPEC-09 §2/§4). */
class ProjectDepartmentBudgetTest {

    private static final TenantId TENANT = TenantId.random();
    private static final UUID DEPARTMENT = UUID.randomUUID();
    private static final UserId ACTOR = UserId.random();

    private final DepartmentBudgetQuery budgets = mock(DepartmentBudgetQuery.class);
    private final UsageMetricsRepository metrics = mock(UsageMetricsRepository.class);

    private ProjectDepartmentBudget useCase(LocalDate today) {
        Clock clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        return new ProjectDepartmentBudget(budgets, metrics, clock);
    }

    private void givenBudget(YearMonth month, BigDecimal amount) {
        when(budgets.budgetFor(TENANT, DEPARTMENT, month)).thenReturn(Optional.of(
            new BudgetLimitInfo(DEPARTMENT, "Marketing", month, amount, 80, false)));
    }

    private void givenCosts(String monthFrom, BigDecimal monthCost,
                            String windowFrom, BigDecimal windowCost) {
        when(metrics.costByDepartment(any())).thenAnswer(invocation -> {
            UsageMetricsFilter filter = invocation.getArgument(0);
            if (filter.from().equals(Instant.parse(monthFrom))) {
                return List.of(new DepartmentCost(DEPARTMENT, 1, 0, 0, monthCost));
            }
            if (filter.from().equals(Instant.parse(windowFrom))) {
                return List.of(new DepartmentCost(DEPARTMENT, 1, 0, 0, windowCost));
            }
            throw new AssertionError("Intervalo inesperado: " + filter.from());
        });
    }

    @Test
    void pj01CalculaComClockFixoEJanelaPadrao() {
        YearMonth month = YearMonth.of(2026, 10);
        givenBudget(month, new BigDecimal("1000.00"));
        givenCosts("2026-10-01T00:00:00Z", new BigDecimal("640.00"),
            "2026-10-14T00:00:00Z", new BigDecimal("400.00"));

        BudgetProjection projection = useCase(LocalDate.of(2026, 10, 27)).query(
            TENANT, DEPARTMENT, month, 14, new VisibilityScope.TenantWide());

        assertThat(projection.today()).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(projection.spentToDate()).isEqualByComparingTo("640.00");
        assertThat(projection.dailyAverage()).isEqualByComparingTo("28.571429");
        assertThat(projection.remainingDays()).isEqualTo(4);
        assertThat(projection.projected()).isEqualByComparingTo("754.29");
        assertThat(projection.projectedPct()).isEqualByComparingTo("75.43");
        assertThat(projection.exhaustionDate()).isNull();
        assertThat(projection.recommendation())
            .isEqualTo(BudgetRecommendation.DENTRO_DO_ORCAMENTO);
        assertThat(projection.calculationVersion()).isEqualTo("projection-v1");
    }

    @Test
    void pj05JanelaAtravessaAMesAnteriorMasOGastoDoMesNao() {
        YearMonth month = YearMonth.of(2026, 11);
        givenBudget(month, new BigDecimal("1000.00"));
        givenCosts("2026-11-01T00:00:00Z", new BigDecimal("300.00"),
            "2026-10-21T00:00:00Z", new BigDecimal("1200.00"));

        BudgetProjection projection = useCase(LocalDate.of(2026, 11, 3)).query(
            TENANT, DEPARTMENT, month, 14, new VisibilityScope.TenantWide());

        assertThat(projection.spentToDate()).isEqualByComparingTo("300.00");
        assertThat(projection.dailyAverage()).isEqualByComparingTo("85.714286");
        assertThat(projection.remainingDays()).isEqualTo(27);
        assertThat(projection.projected()).isEqualByComparingTo("2614.29");

        ArgumentCaptor<UsageMetricsFilter> captor =
            ArgumentCaptor.forClass(UsageMetricsFilter.class);
        verify(metrics, org.mockito.Mockito.times(2)).costByDepartment(captor.capture());
        assertThat(captor.getAllValues()).anySatisfy(filter -> {
            assertThat(filter.tenantId()).isEqualTo(TENANT);
            assertThat(filter.requestedDepartmentId()).isEqualTo(DEPARTMENT);
            assertThat(filter.from()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
            assertThat(filter.to()).isEqualTo(Instant.parse("2026-11-04T00:00:00Z"));
        });
        assertThat(captor.getAllValues()).anySatisfy(filter -> {
            assertThat(filter.from()).isEqualTo(Instant.parse("2026-10-21T00:00:00Z"));
            assertThat(filter.to()).isEqualTo(Instant.parse("2026-11-04T00:00:00Z"));
        });
    }

    @Test
    void pj08RejeitaJanelaForaDeSeteASessentaEMesFuturo() {
        ProjectDepartmentBudget useCase = useCase(LocalDate.of(2026, 10, 27));
        YearMonth month = YearMonth.of(2026, 10);

        assertThatThrownBy(() -> useCase.query(TENANT, DEPARTMENT, month, 6,
            new VisibilityScope.TenantWide()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("janelaDias");
        assertThatThrownBy(() -> useCase.query(TENANT, DEPARTMENT, month, 61,
            new VisibilityScope.TenantWide()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("janelaDias");
        assertThatThrownBy(() -> useCase.query(TENANT, DEPARTMENT,
            YearMonth.of(2026, 11), 14, new VisibilityScope.TenantWide()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("mes futuro");
    }

    @Test
    void departamentoForaDoEscopoEhNegadoSemConsultarOrcamento() {
        assertThatThrownBy(() -> useCase(LocalDate.of(2026, 10, 27)).query(
            TENANT, DEPARTMENT, YearMonth.of(2026, 10), 14,
            new VisibilityScope.Departments(Set.of(UUID.randomUUID()), ACTOR)))
            .isInstanceOf(UsageVisibilityDeniedException.class);
        assertThatThrownBy(() -> useCase(LocalDate.of(2026, 10, 27)).query(
            TENANT, DEPARTMENT, YearMonth.of(2026, 10), 14,
            new VisibilityScope.SelfOnly(ACTOR)))
            .isInstanceOf(UsageVisibilityDeniedException.class);
    }

    @Test
    void departamentoInexistenteNoTenantRetornaNaoEncontrado() {
        when(budgets.budgetFor(TENANT, DEPARTMENT, YearMonth.of(2026, 10)))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase(LocalDate.of(2026, 10, 27)).query(
            TENANT, DEPARTMENT, YearMonth.of(2026, 10), 14,
            new VisibilityScope.TenantWide()))
            .isInstanceOf(ProjectionDepartmentNotFoundException.class);
    }
}
