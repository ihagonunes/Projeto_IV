package br.com.qsoft.governance.observability.infrastructure.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/** Datas ISO do dashboard, mantendo compatibilidade com consumidores de timestamps. */
final class DashboardDateParameter {
    private DashboardDateParameter() { }

    static Instant parse(String value, boolean endOfDay) {
        if (value == null) return null;
        try {
            if (value.length() == 10) {
                LocalDate date = LocalDate.parse(value);
                return endOfDay
                    ? date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1)
                    : date.atStartOfDay(ZoneOffset.UTC).toInstant();
            }
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Data inválida. Use uma data ISO 8601.");
        }
    }
}
