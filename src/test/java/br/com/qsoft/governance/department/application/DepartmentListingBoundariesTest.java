package br.com.qsoft.governance.department.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.department.application.port.DepartmentRepository;
import br.com.qsoft.governance.department.application.port.MonthlyBudgetRepository;
import br.com.qsoft.governance.department.domain.Department;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DepartmentListingBoundariesTest {
    private final TenantId tenant = TenantId.random();
    private final DepartmentRepository departments = mock(DepartmentRepository.class);

    @Test
    void listsOnlyVisibleDepartmentsAndReturnsEmptyForSelfScope() {
        UserId actor = UserId.random();
        var visible = Department.create(UUID.randomUUID(), tenant, "Marketing", null, null, null);
        var hidden = Department.create(UUID.randomUUID(), tenant, "Financeiro", null, null, null);
        var auth = mock(AuthorizationQuery.class);
        when(departments.findByTenantId(tenant)).thenReturn(List.of(visible, hidden));
        when(auth.visibilityFor(tenant, actor, Resource.DEPARTMENT, Action.READ))
            .thenReturn(new VisibilityScope.Departments(Set.of(visible.id()), actor), new VisibilityScope.SelfOnly(actor));
        var query = new ListDepartments(departments, auth);

        assertThat(query.list(tenant, actor, null, null)).containsExactly(visible);
        assertThat(query.list(tenant, actor, null, null)).isEmpty();
    }

    @Test
    void rejectsMissingOrInvertedBudgetMonthsBeforeReadingRepositories() {
        var budgets = mock(MonthlyBudgetRepository.class);
        var query = new ListMonthlyBudgets(departments, budgets);
        UUID id = UUID.randomUUID();
        YearMonth month = YearMonth.of(2026, 7);

        assertThatThrownBy(() -> query.list(tenant, id, null, month)).hasMessageContaining("obrigatórios");
        assertThatThrownBy(() -> query.list(tenant, id, month, null)).hasMessageContaining("obrigatórios");
        assertThatThrownBy(() -> query.list(tenant, id, month.plusMonths(1), month)).hasMessageContaining("anterior");
        verifyNoInteractions(departments, budgets);
    }
}
