package br.com.qsoft.governance.iam.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

class HistoricalIdentityAdapterTest {

    private final RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
    private final HistoricalIdentityAdapter adapter = new HistoricalIdentityAdapter(jdbc);

    @Test
    void returnsEmptyWithoutQueryingWhenThereAreNoUsers() {
        assertThat(adapter.employeeIds(TenantId.random(), null)).isEmpty();
        assertThat(adapter.employeeIds(TenantId.random(), Set.of())).isEmpty();
        assertThat(jdbc.sqls).isEmpty();
    }

    @Test
    void resolvesSingleEmployeeLinkUsingTenantAndUser() {
        TenantId tenant = TenantId.random();
        UserId user = UserId.random();
        jdbc.singleLink = Optional.of("E-7");

        assertThat(adapter.employeeId(tenant, user)).contains("E-7");
        assertThat(jdbc.sqls.getFirst()).contains("l.tenant_id=? AND l.user_id=?", "u.tenant_id=l.tenant_id");
        assertThat(jdbc.params.getFirst()).containsExactly(tenant.value(), user.value());

        jdbc.singleLink = Optional.empty();
        assertThat(adapter.employeeId(TenantId.random(), user)).isEmpty();
    }

    @Test
    void mapsEmployeeIdsAndFiltersByTenant() {
        TenantId tenantId = TenantId.random();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        jdbc.rows.add(new Row(first, "E-1"));
        jdbc.rows.add(new Row(second, "E-2"));

        Map<UserId, String> result = adapter.employeeIds(
            tenantId, Set.of(UserId.of(first), UserId.of(second)));

        assertThat(result)
            .containsEntry(UserId.of(first), "E-1")
            .containsEntry(UserId.of(second), "E-2");
        assertThat(jdbc.sqls.get(0)).contains("l.tenant_id = ?");
        assertThat(jdbc.params).singleElement()
            .satisfies(params -> assertThat(params[0]).isEqualTo(tenantId.value()));
    }

    @Test
    void splitsBatchesAtFiveThousandUsers() {
        Set<UserId> ids = new LinkedHashSet<>();
        for (int i = 0; i < 5_001; i++) {
            ids.add(UserId.random());
        }

        adapter.employeeIds(TenantId.random(), ids);

        assertThat(jdbc.params).hasSize(2);
        assertThat(jdbc.params.get(0)).hasSize(5_001);
        assertThat(jdbc.params.get(1)).hasSize(2);
    }

    private record Row(UUID userId, String employeeId) {
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {

        private final List<String> sqls = new ArrayList<>();
        private final List<Object[]> params = new ArrayList<>();
        private final List<Row> rows = new ArrayList<>();
        private Optional<String> singleLink = Optional.empty();

        @Override
        public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
            sqls.add(sql);
            params.add(args);
            if (singleLink.isEmpty()) {
                return List.of();
            }
            try {
                ResultSet rs = mock(ResultSet.class);
                when(rs.getString(1)).thenReturn(singleLink.orElseThrow());
                return List.of(mapper.mapRow(rs, 0));
            } catch (SQLException ex) {
                throw new AssertionError(ex);
            }
        }

        @Override
        public void query(String sql, RowCallbackHandler rowCallbackHandler, Object... args) {
            sqls.add(sql);
            params.add(args);
            for (Row row : rows) {
                try {
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getObject("user_id", UUID.class)).thenReturn(row.userId());
                    when(rs.getString("legacy_employee_id")).thenReturn(row.employeeId());
                    rowCallbackHandler.processRow(rs);
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }
}
