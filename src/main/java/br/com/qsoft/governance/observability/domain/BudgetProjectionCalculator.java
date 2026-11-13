package br.com.qsoft.governance.observability.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Cálculo puro e determinístico da projeção de orçamento (ADR-41/SPEC-09 §2).
 * <p>
 * Não usa Spring, banco, relógio de parede ou LLM. Para meses passados, o
 * "até hoje" é o mês inteiro e {@code diasRestantes} é zero; para o mês
 * corrente, a data de corte é o dia UTC atual.
 */
public final class BudgetProjectionCalculator {

    private static final int MONEY_SCALE = 2;
    private static final int AVERAGE_SCALE = 6;

    private BudgetProjectionCalculator() {
    }

    public static BudgetProjection calculate(BudgetProjectionFacts facts) {
        Objects.requireNonNull(facts, "facts é obrigatório.");
        YearMonth month = facts.month();
        LocalDate today = facts.today();
        if (month.isAfter(YearMonth.from(today))) {
            throw new IllegalArgumentException("mes futuro não possui projeção.");
        }

        LocalDate asOf = month.isBefore(YearMonth.from(today)) ? month.atEndOfMonth() : today;
        int remainingDays = month.atEndOfMonth().getDayOfMonth() - asOf.getDayOfMonth();
        BigDecimal spent = money(facts.monthCost());
        BigDecimal dailyAverage = facts.windowCost()
            .divide(BigDecimal.valueOf(facts.windowDays()), AVERAGE_SCALE, RoundingMode.HALF_UP);
        BigDecimal projected = spent
            .add(dailyAverage.multiply(BigDecimal.valueOf(remainingDays)))
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal projectedPct = facts.budget() == null || facts.budget().signum() == 0
            ? null
            : projected.multiply(BigDecimal.valueOf(100))
                .divide(facts.budget(), MONEY_SCALE, RoundingMode.HALF_UP);
        LocalDate exhaustionDate = exhaustionDate(facts.budget(), spent, dailyAverage,
            remainingDays, today);
        BudgetRecommendation recommendation = recommendation(facts.budget(), spent, projected,
            projectedPct, facts.alertThresholdPct());

        return new BudgetProjection(month, today, facts.windowDays(), facts.budget(),
            facts.fromDefault(), facts.alertThresholdPct(), spent, dailyAverage, remainingDays,
            projected, projectedPct, exhaustionDate, recommendation,
            BudgetProjection.CALCULATION_VERSION);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static LocalDate exhaustionDate(BigDecimal budget, BigDecimal spent,
                                            BigDecimal dailyAverage, int remainingDays,
                                            LocalDate today) {
        if (budget == null) {
            return null;
        }
        if (budget.signum() == 0) {
            return spent.signum() > 0 ? today : null;
        }
        if (spent.compareTo(budget) >= 0) {
            return today;
        }
        if (dailyAverage.signum() == 0 || remainingDays <= 0) {
            return null;
        }
        for (int day = 1; day <= remainingDays; day++) {
            if (spent.add(dailyAverage.multiply(BigDecimal.valueOf(day)))
                .compareTo(budget) >= 0) {
                return today.plusDays(day);
            }
        }
        return null;
    }

    private static BudgetRecommendation recommendation(BigDecimal budget, BigDecimal spent,
                                                       BigDecimal projected, BigDecimal projectedPct,
                                                       int alertThresholdPct) {
        if (budget == null) {
            return BudgetRecommendation.SEM_ORCAMENTO;
        }
        if (budget.signum() == 0) {
            return spent.signum() > 0
                ? BudgetRecommendation.JA_EXCEDIDO
                : BudgetRecommendation.DENTRO_DO_ORCAMENTO;
        }
        if (spent.compareTo(budget) >= 0) {
            return BudgetRecommendation.JA_EXCEDIDO;
        }
        if (projected.compareTo(budget) >= 0) {
            return BudgetRecommendation.ESGOTA_NO_MES;
        }
        if (projectedPct.compareTo(BigDecimal.valueOf(alertThresholdPct)) >= 0) {
            return BudgetRecommendation.ATENCAO;
        }
        return BudgetRecommendation.DENTRO_DO_ORCAMENTO;
    }
}
