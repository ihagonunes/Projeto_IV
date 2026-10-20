package br.com.qsoft.governance.department.application;

import br.com.qsoft.governance.department.application.port.DepartmentRepository;
import br.com.qsoft.governance.department.application.port.MonthlyBudgetRepository;
import br.com.qsoft.governance.department.domain.MonthlyBudget;
import br.com.qsoft.governance.department.domain.exception.DepartmentNotFoundException;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Upsert do orçamento de um mês (SPEC-02 §4; DEPARTMENT:MANAGE). */
@Service
public class UpsertMonthlyBudget {

    public record Command(TenantId tenantId, UUID departmentId, YearMonth month,
                          BigDecimal amount, int alertThresholdPct, UserId actor) {

        public Command {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(departmentId, "departmentId");
            Objects.requireNonNull(month, "month");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(actor, "actor");
        }
    }

    private final DepartmentRepository departmentRepository;
    private final MonthlyBudgetRepository monthlyBudgetRepository;

    public UpsertMonthlyBudget(DepartmentRepository departmentRepository,
                               MonthlyBudgetRepository monthlyBudgetRepository) {
        this.departmentRepository = departmentRepository;
        this.monthlyBudgetRepository = monthlyBudgetRepository;
    }

    @Transactional
    public MonthlyBudget upsert(Command command) {
        departmentRepository.findByTenantIdAndId(command.tenantId(), command.departmentId())
            .orElseThrow(() -> new DepartmentNotFoundException(command.departmentId()));

        MonthlyBudget budget = monthlyBudgetRepository
            .findByTenantIdAndDepartmentIdAndMonth(command.tenantId(), command.departmentId(),
                command.month())
            .map(existing -> existing.change(command.amount(), command.alertThresholdPct(),
                command.actor().value()))
            .orElseGet(() -> MonthlyBudget.create(UUID.randomUUID(), command.tenantId(),
                command.departmentId(), command.month(), command.amount(),
                command.alertThresholdPct(), command.actor().value()));
        return monthlyBudgetRepository.save(budget);
    }
}
