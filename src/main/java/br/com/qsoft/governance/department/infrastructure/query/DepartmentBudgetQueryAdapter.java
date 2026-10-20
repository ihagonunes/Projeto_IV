package br.com.qsoft.governance.department.infrastructure.query;

import br.com.qsoft.governance.department.api.model.BudgetLimitInfo;
import br.com.qsoft.governance.department.api.query.DepartmentBudgetQuery;
import br.com.qsoft.governance.department.application.port.DepartmentRepository;
import br.com.qsoft.governance.department.application.port.MonthlyBudgetRepository;
import br.com.qsoft.governance.department.domain.BudgetResolution;
import br.com.qsoft.governance.department.domain.Department;
import br.com.qsoft.governance.department.domain.DepartmentStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter que satisfaz o contrato público {@link DepartmentBudgetQuery}
 * (ADR-06 — o department é dono do BudgetLimit mensal; SPEC-02 §2).
 */
@Component
public class DepartmentBudgetQueryAdapter implements DepartmentBudgetQuery {

    private final DepartmentRepository departmentRepository;
    private final MonthlyBudgetRepository monthlyBudgetRepository;

    public DepartmentBudgetQueryAdapter(DepartmentRepository departmentRepository,
                                        MonthlyBudgetRepository monthlyBudgetRepository) {
        this.departmentRepository = departmentRepository;
        this.monthlyBudgetRepository = monthlyBudgetRepository;
    }

    @Override
    public Optional<BigDecimal> monthlyBudgetOf(TenantId tenantId, UUID departmentId) {
        return departmentRepository.findByTenantIdAndId(tenantId, departmentId)
            .map(Department::monthlyBudget)
            .filter(Objects::nonNull);
    }

    @Override
    public Optional<BudgetLimitInfo> budgetFor(TenantId tenantId, UUID departmentId,
                                               YearMonth month) {
        return departmentRepository.findByTenantIdAndId(tenantId, departmentId)
            .map(department -> resolve(department, month));
    }

    @Override
    public List<BudgetLimitInfo> budgetsFor(TenantId tenantId, YearMonth month) {
        return departmentRepository.findByTenantId(tenantId).stream()
            .filter(department -> department.status() == DepartmentStatus.ACTIVE)
            .map(department -> resolve(department, month))
            .toList();
    }

    private BudgetLimitInfo resolve(Department department, YearMonth month) {
        BudgetResolution resolution = BudgetResolution.resolve(
            monthlyBudgetRepository
                .findByTenantIdAndDepartmentIdAndMonth(department.tenantId(), department.id(), month)
                .orElse(null),
            department.monthlyBudget());
        return new BudgetLimitInfo(department.id(), department.name(), month,
            resolution.amount(), resolution.alertThresholdPct(), resolution.fromDefault());
    }
}
