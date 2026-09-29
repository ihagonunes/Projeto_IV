package br.com.qsoft.governance.department.infrastructure.query;

import br.com.qsoft.governance.department.api.query.DepartmentBudgetQuery;
import br.com.qsoft.governance.department.application.port.DepartmentRepository;
import br.com.qsoft.governance.department.domain.Department;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter que satisfaz o contrato público {@link DepartmentBudgetQuery}
 * (ADR-06 — o department é dono do BudgetLimit mensal).
 */
@Component
public class DepartmentBudgetQueryAdapter implements DepartmentBudgetQuery {

    private final DepartmentRepository departmentRepository;

    public DepartmentBudgetQueryAdapter(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Override
    public Optional<BigDecimal> monthlyBudgetOf(TenantId tenantId, UUID departmentId) {
        return departmentRepository.findByTenantIdAndId(tenantId, departmentId)
            .map(Department::monthlyBudget)
            .filter(Objects::nonNull);
    }
}
