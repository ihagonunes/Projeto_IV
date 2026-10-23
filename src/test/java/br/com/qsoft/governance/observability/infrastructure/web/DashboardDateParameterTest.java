package br.com.qsoft.governance.observability.infrastructure.web;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardDateParameterTest {
    @Test
    void datesIncludeEntireUtcDayAndRetainTimestampCompatibility() {
        assertThat(DashboardDateParameter.parse("2026-10-21", false))
            .isEqualTo(Instant.parse("2026-10-21T00:00:00Z"));
        assertThat(DashboardDateParameter.parse("2026-10-21", true))
            .isEqualTo(Instant.parse("2026-10-21T23:59:59.999999999Z"));
        assertThat(DashboardDateParameter.parse("2026-10-21T15:30:00Z", true))
            .isEqualTo(Instant.parse("2026-10-21T15:30:00Z"));
        assertThat(DashboardDateParameter.parse(null, false)).isNull();
    }

    @Test
    void rejectsMalformedDatesWithoutExposingParserDetails() {
        for (String date : new String[]{"2026-02-30", "", "ontem", "2026-10-21T12:00:00"}) {
            assertThatThrownBy(() -> DashboardDateParameter.parse(date, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Data inválida. Use uma data ISO 8601.");
        }
    }
}
