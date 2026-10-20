package br.com.qsoft.governance.observability.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Situação do orçamento de um departamento no mês.
 *
 * @param budget teto do mês; {@code null} = sem orçamento
 * @param actualCost custo realizado no mês (UTC)
 * @param pctUsed percentual usado com 2 casas; {@code null} quando não há orçamento ou o teto é zero
 */
public record BudgetStatusItem(UUID departmentId, String departmentName, BigDecimal budget,
                               BigDecimal actualCost, BigDecimal pctUsed, int alertThresholdPct,
                               BudgetStatus status, boolean fromDefault) {
}
