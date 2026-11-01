package br.com.qsoft.governance.observability.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Entradas do cálculo puro da projeção (ADR-41). Os custos já vêm somados do
 * adaptador nativo tenant-aware; o cálculo não conhece Spring nem banco.
 */
public record BudgetProjectionFacts(
    YearMonth month,
    LocalDate today,
    int windowDays,
    BigDecimal budget,
    boolean fromDefault,
    int alertThresholdPct,
    BigDecimal monthCost,
    BigDecimal windowCost
) {

    public BudgetProjectionFacts {
        Objects.requireNonNull(month, "month é obrigatório.");
        Objects.requireNonNull(today, "today é obrigatório.");
        if (windowDays <= 0) {
            throw new IllegalArgumentException("windowDays deve ser positivo.");
        }
        monthCost = monthCost == null ? BigDecimal.ZERO : monthCost;
        windowCost = windowCost == null ? BigDecimal.ZERO : windowCost;
    }
}
