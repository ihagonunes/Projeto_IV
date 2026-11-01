package br.com.qsoft.governance.observability.infrastructure.web;

import br.com.qsoft.governance.observability.domain.BudgetProjection;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Resposta de {@code GET /api/v1/observability/orcamentos/projecao} (SPEC-09 §1). */
public record BudgetProjectionResponse(
    UUID departmentId,
    String mes,
    LocalDate hoje,
    int janelaDias,
    BigDecimal orcamento,
    @JsonProperty("orcamentoPadrao") boolean orcamentoPadrao,
    int limiarAlerta,
    BigDecimal gastoAteHoje,
    BigDecimal mediaDiaria,
    int diasRestantes,
    BigDecimal projetado,
    BigDecimal pctProjetado,
    LocalDate diaEsgotamento,
    String recomendacao,
    Integer tentativasInconclusivasNaJanela,
    String calculationVersion
) {

    public static BudgetProjectionResponse from(UUID departmentId, BudgetProjection projection) {
        return new BudgetProjectionResponse(
            departmentId,
            projection.month().toString(),
            projection.today(),
            projection.windowDays(),
            projection.budget(),
            projection.fromDefault(),
            projection.alertThresholdPct(),
            projection.spentToDate(),
            projection.dailyAverage(),
            projection.remainingDays(),
            projection.projected(),
            projection.projectedPct(),
            projection.exhaustionDate(),
            projection.recommendation().name(),
            null,
            projection.calculationVersion() == null
                ? BudgetProjection.CALCULATION_VERSION
                : projection.calculationVersion());
    }
}
