package br.com.qsoft.governance.observability.application;

import java.time.YearMonth;
import java.util.List;

/** Relatório de status de orçamento de um mês (SPEC-02 §4). */
public record BudgetStatusReport(YearMonth month, List<BudgetStatusItem> items) {

    public BudgetStatusReport {
        items = List.copyOf(items);
    }
}
