package br.com.qsoft.governance.observability.infrastructure.web;

import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository;
import java.math.BigDecimal;
import java.util.UUID;

/** Tokens e custo por departamento (SPI-89). */
public record DepartmentCostResponse(
    UUID departmentId,
    long requests,
    long inputTokens,
    long outputTokens,
    BigDecimal estimatedCost
) {

    public static DepartmentCostResponse from(UsageMetricsRepository.DepartmentCost cost) {
        return new DepartmentCostResponse(
            cost.departmentId(),
            cost.requests(),
            cost.inputTokens(),
            cost.outputTokens(),
            cost.estimatedCost()
        );
    }
}
