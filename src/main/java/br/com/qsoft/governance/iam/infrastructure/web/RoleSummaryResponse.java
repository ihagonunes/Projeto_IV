package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.application.RoleSummary;

/**
 * Representação pública de um papel de sistema ({@code GET /papeis}).
 */
public record RoleSummaryResponse(
    String key,
    String escopo,
    int posto,
    boolean concedivel
) {

    public static RoleSummaryResponse from(RoleSummary summary) {
        return new RoleSummaryResponse(
            summary.key(), summary.scope(), summary.rank(), summary.grantable());
    }
}
