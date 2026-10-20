package br.com.qsoft.governance.department.domain;

import java.math.BigDecimal;

/**
 * Orçamento efetivo de um departamento num mês: linha própria > padrão
 * ({@code monthly_budget}, limiar 80) > sem orçamento. {@code amount == null}
 * significa "sem orçamento" (nunca zero).
 */
public record BudgetResolution(BigDecimal amount, int alertThresholdPct, boolean fromDefault) {

    public static BudgetResolution resolve(MonthlyBudget monthBudget, BigDecimal defaultAmount) {
        if (monthBudget != null) {
            return new BudgetResolution(monthBudget.amount(), monthBudget.alertThresholdPct(), false);
        }
        if (defaultAmount != null) {
            return new BudgetResolution(defaultAmount, MonthlyBudget.DEFAULT_ALERT_THRESHOLD_PCT,
                true);
        }
        return new BudgetResolution(null, MonthlyBudget.DEFAULT_ALERT_THRESHOLD_PCT, false);
    }

    public boolean hasBudget() {
        return amount != null;
    }
}
