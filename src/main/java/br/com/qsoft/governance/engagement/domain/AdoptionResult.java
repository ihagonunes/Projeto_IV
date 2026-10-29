package br.com.qsoft.governance.engagement.domain;

import java.math.BigDecimal;

/** Resultado de adoção para uma janela (ADR-40 §4). */
public record AdoptionResult(
    int eligible,
    int active,
    int inactive,
    BigDecimal adoptionRate,
    int idleLicenses,
    DataStatus dataStatus
) {
}
