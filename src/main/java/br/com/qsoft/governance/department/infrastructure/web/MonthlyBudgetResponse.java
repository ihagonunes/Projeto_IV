package br.com.qsoft.governance.department.infrastructure.web;

import br.com.qsoft.governance.department.domain.MonthlyBudget;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Representação do orçamento mensal na API. */
public record MonthlyBudgetResponse(String mes, UUID departmentId, BigDecimal valor,
                                    int limiarAlerta, UUID atualizadoPor,
                                    Instant criadoEm, Instant atualizadoEm) {

    public static MonthlyBudgetResponse from(MonthlyBudget budget) {
        return new MonthlyBudgetResponse(
            budget.month().toString(),
            budget.departmentId(),
            budget.amount(),
            budget.alertThresholdPct(),
            budget.updatedBy(),
            budget.createdAt(),
            budget.updatedAt()
        );
    }
}
