package br.com.qsoft.governance.engagement.infrastructure.web;

import br.com.qsoft.governance.engagement.application.AdoptionReport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Resposta de `GET /api/v1/engajamento/adocao` (SPEC-06 §5.1). */
public record AdoptionResponse(
    String sourceKind,
    LocalDate ate,
    int janelaDias,
    int elegiveis,
    int ativos,
    int inativos,
    BigDecimal taxaAdocao,
    int licencasOciosas,
    String dataStatus,
    List<DepartmentAdoptionResponse> porDepartamento,
    CoverageResponse cobertura
) {

    public static AdoptionResponse from(AdoptionReport report) {
        return new AdoptionResponse(
            report.sourceKind().name(),
            report.ate(),
            report.windowDays(),
            report.overall().eligible(),
            report.overall().active(),
            report.overall().inactive(),
            report.overall().adoptionRate(),
            report.overall().idleLicenses(),
            report.overall().dataStatus().name(),
            report.byDepartment().stream().map(DepartmentAdoptionResponse::from).toList(),
            CoverageResponse.from(report.coverage()));
    }
}
