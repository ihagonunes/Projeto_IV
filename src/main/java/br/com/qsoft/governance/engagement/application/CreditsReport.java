package br.com.qsoft.governance.engagement.application;

import br.com.qsoft.governance.engagement.domain.Coverage;
import br.com.qsoft.governance.engagement.domain.SourceKind;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** Resultado dos AI Credits por usuário. */
public record CreditsReport(
    SourceKind sourceKind,
    YearMonth month,
    String status,
    String calculationVersion,
    List<Item> items,
    Coverage coverage,
    Integer personalRank
) {

    public record Item(
        UserId userId,
        String name,
        UUID departmentId,
        BigDecimal score,
        BigDecimal usage,
        BigDecimal training,
        BigDecimal license,
        String licenseApplicability,
        String dataStatus
    ) {
    }
}
