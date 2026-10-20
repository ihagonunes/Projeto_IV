package br.com.qsoft.governance.department.application;

import br.com.qsoft.governance.department.application.port.DepartmentRepository;
import br.com.qsoft.governance.department.application.port.MonthlyBudgetRepository;
import br.com.qsoft.governance.department.domain.MonthlyBudget;
import br.com.qsoft.governance.department.domain.exception.DepartmentNotFoundException;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Histórico de orçamentos do departamento (SPI-125), no máximo 24 meses. */
@Service
public class ListMonthlyBudgets {

    public static final int MAX_MONTHS = 24;

    private final DepartmentRepository departmentRepository;
    private final MonthlyBudgetRepository monthlyBudgetRepository;

    public ListMonthlyBudgets(DepartmentRepository departmentRepository,
                              MonthlyBudgetRepository monthlyBudgetRepository) {
        this.departmentRepository = departmentRepository;
        this.monthlyBudgetRepository = monthlyBudgetRepository;
    }

    @Transactional(readOnly = true)
    public List<MonthlyBudget> list(TenantId tenantId, UUID departmentId,
                                    YearMonth from, YearMonth to) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(departmentId, "departmentId");
        if (from == null || to == null) {
            throw new IllegalArgumentException("de e ate são obrigatórios.");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("de deve ser anterior ou igual a ate.");
        }
        if (ChronoUnit.MONTHS.between(from, to) + 1 > MAX_MONTHS) {
            throw new IllegalArgumentException("O intervalo máximo é de 24 meses.");
        }
        departmentRepository.findByTenantIdAndId(tenantId, departmentId)
            .orElseThrow(() -> new DepartmentNotFoundException(departmentId));
        return monthlyBudgetRepository.findRange(tenantId, departmentId, from, to);
    }
}
