package br.com.qsoft.governance.observability.infrastructure.web;

import br.com.qsoft.governance.observability.domain.UsageRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Representação pública de um UsageRecord. */
public record UsageRecordResponse(
    UUID id,
    UUID requestId,
    UUID departmentId,
    UUID userId,
    UUID modelId,
    UUID providerId,
    long inputTokens,
    long outputTokens,
    BigDecimal estimatedCost,
    long latencyMs,
    String status,
    Instant createdAt
) {

    public static UsageRecordResponse from(UsageRecord record) {
        return new UsageRecordResponse(
            record.id(),
            record.requestId(),
            record.departmentId(),
            record.userId(),
            record.modelId(),
            record.providerId(),
            record.inputTokens(),
            record.outputTokens(),
            record.estimatedCost(),
            record.latencyMs(),
            record.status(),
            record.createdAt()
        );
    }
}
