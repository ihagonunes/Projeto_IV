package br.com.qsoft.governance.observability.application;

import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Normaliza e valida o <b>filtro de período</b> do dashboard (SPI-90):
 * intervalo padrão de <b>30 dias</b> quando ausente e validação de consistência
 * temporal ({@code from <= to}).
 */
final class DashboardPeriod {

    static final Duration DEFAULT_RANGE = Duration.ofDays(30);

    private DashboardPeriod() {
    }

    static Window resolve(Instant from, Instant to) {
        Instant resolvedTo = to == null ? Instant.now() : to;
        Instant resolvedFrom = from == null ? resolvedTo.minus(DEFAULT_RANGE) : from;
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new IllegalArgumentException("'from' não pode ser posterior a 'to'.");
        }
        return new Window(resolvedFrom, resolvedTo);
    }

    record Window(Instant from, Instant to) {
        Window {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
        }
    }
}
