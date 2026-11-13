package br.com.qsoft.governance.observability.application.port;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HistoricalDashboardQueryTest {
    @Test
    void countOnlyDailyPointHasNoEstimatedCost() {
        var daily = new HistoricalDashboardQuery.Daily("2026-07-01", 7);

        assertThat(daily).isEqualTo(new HistoricalDashboardQuery.Daily("2026-07-01", 7, 0, 0, null, 0));
    }
}
