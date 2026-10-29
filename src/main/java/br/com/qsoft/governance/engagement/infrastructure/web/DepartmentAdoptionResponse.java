package br.com.qsoft.governance.engagement.infrastructure.web;

import br.com.qsoft.governance.engagement.application.AdoptionReport;
import java.math.BigDecimal;
import java.util.UUID;

/** Adoção agrupada pela lotação IAM. */
public record DepartmentAdoptionResponse(
    UUID departmentId,
    int elegiveis,
    int ativos,
    BigDecimal taxaAdocao
) {

    public static DepartmentAdoptionResponse from(
            AdoptionReport.DepartmentAdoption department) {
        return new DepartmentAdoptionResponse(
            department.departmentId(),
            department.result().eligible(),
            department.result().active(),
            department.result().adoptionRate());
    }
}
