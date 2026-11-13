package br.com.qsoft.governance.observability.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import br.com.qsoft.governance.observability.application.port.HistoricalDashboardQuery.Coverage;
import br.com.qsoft.governance.observability.application.port.HistoricalDashboardQuery.Daily;
import br.com.qsoft.governance.observability.application.port.HistoricalDashboardQuery.Filter;
import br.com.qsoft.governance.observability.application.port.HistoricalDashboardQuery.ModelCost;
import br.com.qsoft.governance.observability.application.port.HistoricalDashboardQuery.Option;
import br.com.qsoft.governance.observability.application.port.HistoricalDashboardQuery.Scope;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class HistoricalDashboardAdapterTest {

    private final TenantId tenant = TenantId.random();
    private final DashboardJdbc jdbc = new DashboardJdbc();
    private final HistoricalDashboardAdapter adapter = new HistoricalDashboardAdapter(jdbc);

    @Test
    void mapsCoverageAndOptionsFromTenantUsage() {
        jdbc.columns = Map.of("first_day", "2026-07-01", "last_day", "2026-07-18");
        assertThat(adapter.coverage(tenant)).isEqualTo(new Coverage(LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 18)));
        assertTenantOnly();
        jdbc.columns = Map.of();
        assertThat(adapter.coverage(tenant)).isEqualTo(new Coverage(null, null));

        jdbc.columns = Map.of(1, "department-1", 2, "Marketing");
        assertThat(adapter.departments(tenant)).containsExactly(new Option("department-1", "Marketing"));
        assertTenantOnly();
        assertThat(jdbc.sql).contains("d.company_id=u.company_id", "u.department_id IS NOT NULL");

        jdbc.columns = Map.of(1, "model-1", 2, "Modelo");
        assertThat(adapter.models(tenant)).containsExactly(new Option("model-1", "Modelo"));
        assertTenantOnly();
        assertThat(jdbc.sql).contains("u.model_id IS NOT NULL");
    }

    @Test
    void appliesScopedFiltersAndMapsSeriesAndModelCosts() {
        Filter filter = new Filter(tenant, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
            "dept-1", "model-1", new Scope(false, Set.of("dept-1"), "self"), "target");
        BigDecimal cost = new BigDecimal("0.15");
        jdbc.columns = Map.of("period_day", "2026-07-18", "requests", 2L, "input_tokens", 3L,
            "output_tokens", 4L, "estimated_cost", cost, "unpriced", 1L, "model_id", "model-1", "name", "Modelo");

        assertThat(adapter.series(filter)).containsExactly(new Daily("2026-07-18", 2, 3, 4, cost, 1));
        assertScopedFilters();
        assertThat(adapter.costs(filter)).containsExactly(new ModelCost("model-1", "Modelo", 2, 3, 4, cost, 1));
        assertScopedFilters();
        assertThat(adapter.activeUsers(filter)).isEqualTo(7);
        assertScopedFilters();
        assertThat(jdbc.sql).contains("COUNT(DISTINCT u.employee_id)", "u.request_count > 0");
    }

    @Test
    void emptyRestrictedScopeAddsAlwaysFalsePredicate() {
        Filter emptyScope = new Filter(tenant, null, null, null, null, new Scope(false, Set.of(), null));

        adapter.activeUsers(emptyScope);

        assertThat(jdbc.sql).contains("u.company_id = ? AND (FALSE)");
        assertThat(jdbc.arguments).containsExactly(tenant.toString());
    }

    private void assertTenantOnly() {
        assertThat(jdbc.sql).contains("u.company_id = ?");
        assertThat(jdbc.arguments).containsExactly(tenant.toString());
    }

    private void assertScopedFilters() {
        assertThat(jdbc.sql).contains("u.company_id = ?",
            "AND (FALSE OR u.employee_id = ? OR u.department_id IN (?))",
            "u.\"timestamp\" >= ?", "u.\"timestamp\" < ?", "u.department_id = ?", "u.model_id = ?");
        assertThat(jdbc.arguments).containsExactly(tenant.toString(), "self", "dept-1",
            "2026-07-01", "2026-08-01", "dept-1", "model-1", "target");
    }

    private static final class DashboardJdbc extends JdbcTemplate {
        private Map<Object, Object> columns = Map.of();
        private String sql;
        private List<Object> arguments;

        @Override
        public <T> List<T> query(String statement, RowMapper<T> mapper, Object... parameters) {
            return List.of(queryForObject(statement, mapper, parameters));
        }

        @Override
        public <T> T queryForObject(String statement, RowMapper<T> mapper, Object... parameters) {
            recordQuery(statement, parameters);
            ResultSet row = mock(ResultSet.class, invocation -> columns.get(invocation.getArgument(0)));
            try {
                return mapper.mapRow(row, 0);
            } catch (SQLException ex) {
                throw new AssertionError(ex);
            }
        }

        @Override
        public <T> T queryForObject(String statement, Class<T> type, Object... parameters) {
            recordQuery(statement, parameters);
            return type.cast(7L);
        }

        private void recordQuery(String statement, Object[] parameters) {
            sql = statement;
            arguments = List.of(parameters);
        }
    }
}
