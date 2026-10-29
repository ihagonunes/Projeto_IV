package br.com.qsoft.governance.engagement.application;

import br.com.qsoft.governance.engagement.domain.AdoptionResult;
import br.com.qsoft.governance.engagement.domain.Coverage;
import br.com.qsoft.governance.engagement.domain.SourceKind;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Resultado completo da adoção, com recorte e cobertura. */
public record AdoptionReport(
    SourceKind sourceKind,
    LocalDate ate,
    int windowDays,
    AdoptionResult overall,
    List<DepartmentAdoption> byDepartment,
    Coverage coverage
) {

    public record DepartmentAdoption(UUID departmentId, AdoptionResult result) {
    }
}
