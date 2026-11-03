package br.com.qsoft.governance.engagement.infrastructure.web;

import br.com.qsoft.governance.engagement.application.CreditsReport;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Resposta de `GET /api/v1/engajamento/creditos` (SPEC-06 §5.3). */
public record CreditsResponse(
    String sourceKind,
    String mes,
    String status,
    String calculationVersion,
    List<CreditsItemResponse> itens,
    CoverageResponse cobertura
) {

    public static CreditsResponse from(CreditsReport report) {
        return new CreditsResponse(
            report.sourceKind().name(),
            report.month().toString(),
            report.status(),
            report.calculationVersion(),
            report.items().stream().map(CreditsItemResponse::from).toList(),
            CoverageResponse.from(report.coverage()));
    }

    public record CreditsItemResponse(
        UUID userId,
        String nome,
        UUID departmentId,
        BigDecimal score,
        BigDecimal usage,
        BigDecimal training,
        BigDecimal license,
        String licenseApplicability,
        String dataStatus
    ) {

        public static CreditsItemResponse from(CreditsReport.Item item) {
            return new CreditsItemResponse(
                item.userId().value(), item.name(), item.departmentId(), item.score(),
                item.usage(), item.training(), item.license(),
                item.licenseApplicability(), item.dataStatus());
        }
    }
}
