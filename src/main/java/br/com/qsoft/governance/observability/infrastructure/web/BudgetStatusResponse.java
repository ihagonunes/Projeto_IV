package br.com.qsoft.governance.observability.infrastructure.web;

import br.com.qsoft.governance.observability.application.BudgetStatusItem;
import br.com.qsoft.governance.observability.application.BudgetStatusReport;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Resposta de {@code GET /api/v1/observability/orcamentos} (SPEC-02 §4). */
public record BudgetStatusResponse(String mes, List<Item> itens) {

    public record Item(
        UUID departmentId,
        String departmentName,
        BigDecimal orcamento,
        BigDecimal custoReal,
        BigDecimal pctUsado,
        int limiarAlerta,
        String status,
        @JsonProperty("orcamentoPadrao") boolean orcamentoPadrao) {
    }

    public static BudgetStatusResponse from(BudgetStatusReport report) {
        List<Item> itens = report.items().stream()
            .map(BudgetStatusResponse::item)
            .toList();
        return new BudgetStatusResponse(report.month().toString(), itens);
    }

    private static Item item(BudgetStatusItem item) {
        return new Item(item.departmentId(), item.departmentName(), item.budget(),
            item.actualCost(), item.pctUsed(), item.alertThresholdPct(), item.status().name(),
            item.fromDefault());
    }
}
