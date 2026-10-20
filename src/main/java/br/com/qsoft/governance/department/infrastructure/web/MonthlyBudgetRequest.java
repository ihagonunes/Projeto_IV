package br.com.qsoft.governance.department.infrastructure.web;

import java.math.BigDecimal;

/** Corpo do PUT de orçamento mensal. */
public record MonthlyBudgetRequest(BigDecimal amount, Integer alertThresholdPct) {
}
