package br.com.qsoft.governance.department.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.department.api.model.BudgetLimitInfo;
import br.com.qsoft.governance.department.application.port.DepartmentRepository;
import br.com.qsoft.governance.department.application.port.MonthlyBudgetRepository;
import br.com.qsoft.governance.department.domain.Department;
import br.com.qsoft.governance.department.domain.MonthlyBudget;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Resolução do orçamento por mês no contrato público (SPEC-02 §2). */
class DepartmentBudgetQueryAdapterTest {

    private final TenantId tenantId = TenantId.random();
    private final YearMonth month = YearMonth.of(2026, 10);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final MonthlyBudgetRepository budgets = mock(MonthlyBudgetRepository.class);
    private final DepartmentBudgetQueryAdapter adapter =
        new DepartmentBudgetQueryAdapter(departments, budgets);

    private Department department(String name, BigDecimal defaultBudget) {
        return Department.create(UUID.randomUUID(), tenantId, name, null, null, defaultBudget);
    }

    private void monthRow(Department department, BigDecimal amount, int threshold) {
        when(budgets.findByTenantIdAndDepartmentIdAndMonth(tenantId, department.id(), month))
            .thenReturn(Optional.of(MonthlyBudget.create(UUID.randomUUID(), tenantId,
                department.id(), month, amount, threshold, UUID.randomUUID())));
    }

    @Test
    void legacyLookupReturnsOnlyTheDefaultBudgetWithinTenant() {
        Department configured = department("Marketing", BigDecimal.TEN);
        Department missingBudget = department("Engenharia", null);
        when(departments.findByTenantIdAndId(tenantId, configured.id())).thenReturn(Optional.of(configured));
        when(departments.findByTenantIdAndId(tenantId, missingBudget.id())).thenReturn(Optional.of(missingBudget));

        assertThat(adapter.monthlyBudgetOf(tenantId, configured.id())).contains(BigDecimal.TEN);
        assertThat(adapter.monthlyBudgetOf(tenantId, missingBudget.id())).isEmpty();
        assertThat(adapter.monthlyBudgetOf(TenantId.random(), configured.id())).isEmpty();
    }

    @Test
    void linhaDoMesVenceOPadrao() {
        Department marketing = department("Marketing", new BigDecimal("1000.00"));
        when(departments.findByTenantIdAndId(tenantId, marketing.id()))
            .thenReturn(Optional.of(marketing));
        monthRow(marketing, new BigDecimal("500.00"), 50);

        BudgetLimitInfo info = adapter.budgetFor(tenantId, marketing.id(), month).orElseThrow();

        assertThat(info.amount()).isEqualByComparingTo("500.00");
        assertThat(info.alertThresholdPct()).isEqualTo(50);
        assertThat(info.fromDefault()).isFalse();
        assertThat(info.departmentName()).isEqualTo("Marketing");
    }

    @Test
    void semLinhaDoMesUsaOPadraoComLimiar80() {
        Department marketing = department("Marketing", new BigDecimal("1000.00"));
        when(departments.findByTenantIdAndId(tenantId, marketing.id()))
            .thenReturn(Optional.of(marketing));
        when(budgets.findByTenantIdAndDepartmentIdAndMonth(tenantId, marketing.id(), month))
            .thenReturn(Optional.empty());

        BudgetLimitInfo info = adapter.budgetFor(tenantId, marketing.id(), month).orElseThrow();

        assertThat(info.amount()).isEqualByComparingTo("1000.00");
        assertThat(info.alertThresholdPct()).isEqualTo(80);
        assertThat(info.fromDefault()).isTrue();
    }

    @Test
    void departamentoExistenteSemOrcamentoRetornaAmountNulo() {
        Department marketing = department("Marketing", null);
        when(departments.findByTenantIdAndId(tenantId, marketing.id()))
            .thenReturn(Optional.of(marketing));
        when(budgets.findByTenantIdAndDepartmentIdAndMonth(tenantId, marketing.id(), month))
            .thenReturn(Optional.empty());

        BudgetLimitInfo info = adapter.budgetFor(tenantId, marketing.id(), month).orElseThrow();

        assertThat(info.amount()).isNull();
        assertThat(info.fromDefault()).isFalse();
    }

    @Test
    void departamentoDeOutroTenantNaoEEncontrado() {
        when(departments.findByTenantIdAndId(tenantId, UUID.randomUUID()))
            .thenReturn(Optional.empty());

        assertThat(adapter.budgetFor(tenantId, UUID.randomUUID(), month)).isEmpty();
    }

    @Test
    void budgetsForIncluiSomenteAtivosESemOrcamentoTambem() {
        Department marketing = department("Marketing", new BigDecimal("1000.00"));
        Department engenharia = department("Engenharia", null);
        Department antigo = department("Antigo", BigDecimal.TEN);
        antigo.deactivate();
        when(departments.findByTenantId(tenantId))
            .thenReturn(List.of(marketing, engenharia, antigo));
        when(budgets.findByTenantIdAndDepartmentIdAndMonth(tenantId, marketing.id(), month))
            .thenReturn(Optional.empty());
        when(budgets.findByTenantIdAndDepartmentIdAndMonth(tenantId, engenharia.id(), month))
            .thenReturn(Optional.empty());

        List<BudgetLimitInfo> result = adapter.budgetsFor(tenantId, month);

        assertThat(result).extracting(BudgetLimitInfo::departmentName)
            .containsExactly("Marketing", "Engenharia");
        assertThat(result).filteredOn(i -> i.departmentName().equals("Engenharia"))
            .allSatisfy(i -> assertThat(i.amount()).isNull());
    }
}
