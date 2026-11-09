package br.com.qsoft.governance.observability.infrastructure.web;

import br.com.qsoft.governance.observability.application.UsageMetricsSeries;
import java.time.Instant;
import java.util.List;

/** Série temporal + usuários ativos (SPI-88). */
public record UsageMetricsResponse(
    String granularity,
    Instant from,
    Instant to,
    long activeUsers,
    List<SeriesPoint> series
) {

    public record SeriesPoint(String period, long requests, long inputTokens, long outputTokens,
                              java.math.BigDecimal estimatedCost) {
    }

    public static UsageMetricsResponse from(UsageMetricsSeries metrics) {
        return new UsageMetricsResponse(
            metrics.granularity(),
            metrics.from(),
            metrics.to(),
            metrics.activeUsers(),
            metrics.series().stream()
                .map(point -> new SeriesPoint(point.period(), point.requests(), point.inputTokens(),
                    point.outputTokens(), point.estimatedCost()))
                .toList()
        );
    }
}
