package br.com.qsoft.governance.department.api.model;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Orçamento efetivo de um departamento num mês (SPEC-02 §2).
 *
 * @param amount teto do mês; {@code null} significa "sem orçamento" (nunca zero)
 * @param fromDefault {@code true} quando veio do {@code monthly_budget} padrão do departamento
 */
public record BudgetLimitInfo(UUID departmentId, String departmentName, YearMonth month,
                              BigDecimal amount, int alertThresholdPct, boolean fromDefault) {
}
