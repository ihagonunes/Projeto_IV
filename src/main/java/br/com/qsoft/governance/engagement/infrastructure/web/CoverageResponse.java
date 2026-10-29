package br.com.qsoft.governance.engagement.infrastructure.web;

import br.com.qsoft.governance.engagement.domain.Coverage;

/** Cobertura da fonte de engajamento. */
public record CoverageResponse(
    int eligibleTotal,
    int eligibleLinked,
    int unmappedLicenseStatus,
    int unmappedTrainingStatus
) {

    public static CoverageResponse from(Coverage coverage) {
        return new CoverageResponse(coverage.eligibleTotal(), coverage.eligibleLinked(),
            coverage.unmappedLicenseStatus(), coverage.unmappedTrainingStatus());
    }
}
