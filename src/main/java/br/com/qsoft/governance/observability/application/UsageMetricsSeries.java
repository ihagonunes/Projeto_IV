package br.com.qsoft.governance.observability.application;

import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository;
import java.time.Instant;
import java.util.List;

/** Série temporal + usuários ativos (SPI-88). */
public record UsageMetricsSeries(
    String granularity,
    Instant from,
    Instant to,
    long activeUsers,
    List<UsageMetricsRepository.UsageSeriesPoint> series
) {
}
