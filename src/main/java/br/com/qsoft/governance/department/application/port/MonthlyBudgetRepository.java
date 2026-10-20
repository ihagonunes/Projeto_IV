package br.com.qsoft.governance.department.application.port;

import br.com.qsoft.governance.department.domain.MonthlyBudget;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistência do orçamento mensal (tenant + departamento + mês). */
public interface MonthlyBudgetRepository {

    Optional<MonthlyBudget> findByTenantIdAndDepartmentIdAndMonth(TenantId tenantId,
                                                                  UUID departmentId,
                                                                  YearMonth month);

    List<MonthlyBudget> findRange(TenantId tenantId, UUID departmentId,
                                  YearMonth from, YearMonth to);

    MonthlyBudget save(MonthlyBudget budget);
}
