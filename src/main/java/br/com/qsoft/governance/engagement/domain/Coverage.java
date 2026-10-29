package br.com.qsoft.governance.engagement.domain;

/** Cobertura dos fatos de engajamento (ADR-40 §4). */
public record Coverage(
    int eligibleTotal,
    int eligibleLinked,
    int unmappedLicenseStatus,
    int unmappedTrainingStatus
) {
}
