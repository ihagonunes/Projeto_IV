package br.com.qsoft.governance.engagement.domain;

import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.math.BigDecimal;

/** AI Credits de um usuário em um mês (ADR-19/ADR-40 §7). */
public record CreditsItem(
    UserId userId,
    BigDecimal usage,
    BigDecimal training,
    BigDecimal license,
    LicenseApplicability licenseApplicability,
    BigDecimal score,
    DataStatus dataStatus
) {
}
