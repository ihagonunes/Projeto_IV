package br.com.qsoft.governance.department.infrastructure.persistence;

import br.com.qsoft.governance.department.domain.MonthlyBudget;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.LocalDate;
import java.time.YearMonth;

/** Converte entre o orçamento de domínio e a entidade JPA. */
public final class MonthlyBudgetJpaMapper {

    public MonthlyBudgetJpaEntity toEntity(MonthlyBudget budget) {
        MonthlyBudgetJpaEntity entity = new MonthlyBudgetJpaEntity();
        entity.setId(budget.id());
        entity.setTenantId(budget.tenantId().value());
        entity.setDepartmentId(budget.departmentId());
        entity.setMonth(budget.month().atDay(1));
        entity.setAmount(budget.amount());
        entity.setAlertThresholdPct((short) budget.alertThresholdPct());
        entity.setUpdatedBy(budget.updatedBy());
        entity.setCreatedAt(budget.createdAt());
        entity.setUpdatedAt(budget.updatedAt());
        return entity;
    }

    public MonthlyBudget toDomain(MonthlyBudgetJpaEntity entity) {
        LocalDate month = entity.getMonth();
        return MonthlyBudget.restore(
            entity.getId(),
            TenantId.of(entity.getTenantId()),
            entity.getDepartmentId(),
            YearMonth.of(month.getYear(), month.getMonth()),
            entity.getAmount(),
            entity.getAlertThresholdPct(),
            entity.getUpdatedBy(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
