package br.com.qsoft.governance.observability.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Resultado determinístico da projeção de orçamento (ADR-41/SPEC-09).
 *
 * @param spentToDate gasto do mês até hoje (mês passado: mês inteiro)
 * @param dailyAverage média diária da janela, escala 6, HALF_UP
 * @param projected gasto até hoje + média diária × dias restantes, escala 2
 * @param projectedPct percentual projetado; nulo sem orçamento ou com orçamento zero
 */
public record BudgetProjection(
    YearMonth month,
    LocalDate today,
    int windowDays,
    BigDecimal budget,
    boolean fromDefault,
    int alertThresholdPct,
    BigDecimal spentToDate,
    BigDecimal dailyAverage,
    int remainingDays,
    BigDecimal projected,
    BigDecimal projectedPct,
    LocalDate exhaustionDate,
    BudgetRecommendation recommendation,
    String calculationVersion
) {

    public static final String CALCULATION_VERSION = "projection-v1";
}
