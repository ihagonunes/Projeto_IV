package br.com.qsoft.governance.observability.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

class UsageActivityJdbcAdapterTest {

    private final RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
    private final UsageActivityJdbcAdapter adapter = new UsageActivityJdbcAdapter(jdbc);

    @Test
    void returnsEmptyWithoutQueryingWhenThereAreNoUsers() {
        assertThat(adapter.activeDays(TenantId.random(), null,
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))).isEmpty();
        assertThat(adapter.activeDays(TenantId.random(), Set.of(),
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))).isEmpty();
        assertThat(jdbc.sqls).isEmpty();
    }

    @Test
    void mapsUtcDaysAndUsesInclusiveWindow() {
        TenantId tenantId = TenantId.random();
        UUID userId = UUID.randomUUID();
        jdbc.rows.add(new Row(userId, LocalDate.of(2026, 7, 1)));

        Map<UserId, Set<LocalDate>> result = adapter.activeDays(
            tenantId, Set.of(UserId.of(userId)),
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));

        assertThat(result.get(UserId.of(userId)))
            .containsExactly(LocalDate.of(2026, 7, 1));
        assertThat(jdbc.sqls.get(0))
            .contains("tenant_id = ?")
            .contains("AT TIME ZONE 'UTC'");
        assertThat(jdbc.params).singleElement().satisfies(params -> {
            assertThat(params[0]).isEqualTo(tenantId.value());
            assertThat(params[1]).isEqualTo(userId);
            assertThat(((Timestamp) params[2]).toInstant())
                .isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
            assertThat(((Timestamp) params[3]).toInstant())
                .isEqualTo(Instant.parse("2026-07-02T00:00:00Z"));
        });
    }

    @Test
    void splitsBatchesAtFiveThousandUsers() {
        Set<UserId> ids = new LinkedHashSet<>();
        for (int i = 0; i < 5_001; i++) {
            ids.add(UserId.random());
        }

        adapter.activeDays(TenantId.random(), ids,
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertThat(jdbc.params).hasSize(2);
        assertThat(jdbc.params.get(0)).hasSize(5_003);
        assertThat(jdbc.params.get(1)).hasSize(4);
    }

    @Test
    void rejectsInvertedRange() {
        assertThatThrownBy(() -> adapter.activeDays(TenantId.random(), Set.of(UserId.random()),
            LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private record Row(UUID userId, LocalDate day) {
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {

        private final List<String> sqls = new ArrayList<>();
        private final List<Object[]> params = new ArrayList<>();
        private final List<Row> rows = new ArrayList<>();

        @Override
        public void query(String sql, RowCallbackHandler rowCallbackHandler, Object... args) {
            sqls.add(sql);
            params.add(args);
            for (Row row : rows) {
                try {
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getObject("user_id", UUID.class)).thenReturn(row.userId());
                    when(rs.getDate("active_day")).thenReturn(java.sql.Date.valueOf(row.day()));
                    rowCallbackHandler.processRow(rs);
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }
}
