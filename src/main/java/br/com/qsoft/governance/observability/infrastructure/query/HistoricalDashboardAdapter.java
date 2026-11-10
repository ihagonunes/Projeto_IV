package br.com.qsoft.governance.observability.infrastructure.query;

import br.com.qsoft.governance.observability.application.port.HistoricalDashboardQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class HistoricalDashboardAdapter implements HistoricalDashboardQuery {
    private final JdbcTemplate jdbc;

    public HistoricalDashboardAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Coverage coverage(TenantId tenant) {
        return coverage(new Filter(tenant, null, null, null, null));
    }

    @Override
    public Coverage coverage(Filter filter) {
        var w = where(filter);
        return jdbc.queryForObject("""
            SELECT MIN(LEFT("timestamp", 10)) first_day, MAX(LEFT("timestamp", 10)) last_day
            FROM historical.ai_usage_logs u
            """ + w.sql(), (rs, n) -> new Coverage(
                rs.getString("first_day") == null ? null : LocalDate.parse(rs.getString("first_day")),
                rs.getString("last_day") == null ? null : LocalDate.parse(rs.getString("last_day"))),
            w.params());
    }

    @Override
    public List<Option> departments(TenantId tenant) {
        return departments(new Filter(tenant, null, null, null, null));
    }

    @Override
    public List<Option> departments(Filter filter) {
        var w = where(filter);
        return jdbc.query("""
            SELECT DISTINCT u.department_id, COALESCE(d.department_name,u.department_id) name
            FROM historical.ai_usage_logs u LEFT JOIN historical.departments d
            ON d.company_id=u.company_id AND d.department_id=u.department_id
            """ + w.sql() + " AND u.department_id IS NOT NULL ORDER BY name, u.department_id",
            (rs, n) -> new Option(rs.getString(1), rs.getString(2)), w.params());
    }

    @Override
    public List<Option> models(TenantId tenant) {
        return models(new Filter(tenant, null, null, null, null));
    }

    @Override
    public List<Option> models(Filter filter) {
        var w = where(filter);
        // Catálogo global só fica visível quando existe uso do modelo no tenant.
        return jdbc.query("""
            SELECT DISTINCT u.model_id, COALESCE(m.model_name, u.model_id) name
            FROM historical.ai_usage_logs u
            LEFT JOIN historical.ai_models m ON m.model_id = u.model_id
            """ + w.sql() + " AND u.model_id IS NOT NULL ORDER BY name, u.model_id",
            (rs, n) -> new Option(rs.getString(1), rs.getString(2)), w.params());
    }

    private record Where(String sql, Object[] params) { }

    private Where where(Filter f) {
        var params = new ArrayList<Object>();
        params.add(f.tenantId().value().toString());
        String sql = " WHERE u.company_id = ?";
        if (!f.scope().tenantWide()) {
            sql += " AND (FALSE";
            if (f.scope().employeeId() != null) { sql += " OR u.employee_id = ?"; params.add(f.scope().employeeId()); }
            if (!f.scope().departmentIds().isEmpty()) {
                sql += " OR u.department_id IN (" + String.join(",", Collections.nCopies(f.scope().departmentIds().size(), "?")) + ")";
                params.addAll(f.scope().departmentIds());
            }
            sql += ")";
        }
        if (f.from() != null) { sql += " AND u.\"timestamp\" >= ?"; params.add(f.from().toString()); }
        if (f.to() != null) { sql += " AND u.\"timestamp\" < ?"; params.add(f.to().plusDays(1).toString()); }
        if (f.departmentId() != null) { sql += " AND u.department_id = ?"; params.add(f.departmentId()); }
        if (f.modelId() != null) { sql += " AND u.model_id = ?"; params.add(f.modelId()); }
        if (f.requestedEmployeeId() != null) { sql += " AND u.employee_id = ?"; params.add(f.requestedEmployeeId()); }
        return new Where(sql, params.toArray());
    }

    @Override
    public long activeUsers(Filter filter) {
        var w = where(filter);
        return jdbc.queryForObject("SELECT COUNT(DISTINCT u.employee_id) FROM historical.ai_usage_logs u"
            + w.sql() + " AND u.request_count > 0", Long.class, w.params());
    }

    @Override
    public List<Daily> series(Filter filter) {
        var w = where(filter);
        return jdbc.query("""
            SELECT LEFT(u."timestamp", 10) AS period_day, COALESCE(SUM(u.request_count),0) requests,
                COALESCE(SUM(u.input_tokens),0) input_tokens,
                COALESCE(SUM(u.output_tokens),0) output_tokens,
                SUM(CASE WHEN m.input_price_usd_per_1k_tokens >= 0
                         AND m.output_price_usd_per_1k_tokens >= 0
                         AND u.input_tokens IS NOT NULL AND u.output_tokens IS NOT NULL
                    THEN (u.input_tokens::numeric * m.input_price_usd_per_1k_tokens::numeric
                         + u.output_tokens::numeric * m.output_price_usd_per_1k_tokens::numeric) / 1000
                    END) estimated_cost,
                COUNT(*) FILTER (WHERE m.input_price_usd_per_1k_tokens IS NULL
                    OR m.output_price_usd_per_1k_tokens IS NULL
                    OR m.input_price_usd_per_1k_tokens < 0 OR m.output_price_usd_per_1k_tokens < 0
                    OR u.input_tokens IS NULL OR u.output_tokens IS NULL) unpriced
            FROM historical.ai_usage_logs u
            LEFT JOIN historical.ai_models m ON m.model_id = u.model_id
            """ + w.sql() + " GROUP BY period_day ORDER BY period_day",
            (rs, n) -> new Daily(rs.getString("period_day"), rs.getLong("requests"),
                rs.getLong("input_tokens"), rs.getLong("output_tokens"),
                rs.getBigDecimal("estimated_cost"), rs.getLong("unpriced")), w.params());
    }

    @Override
    public List<ModelCost> costs(Filter filter) {
        var w = where(filter);
        return jdbc.query("""
            SELECT u.model_id, COALESCE(m.model_name, u.model_id, 'Sem modelo') name,
                COALESCE(SUM(u.request_count),0) requests,
                COALESCE(SUM(u.input_tokens),0) input_tokens,
                COALESCE(SUM(u.output_tokens),0) output_tokens,
                SUM(CASE WHEN m.input_price_usd_per_1k_tokens >= 0
                         AND m.output_price_usd_per_1k_tokens >= 0
                         AND u.input_tokens IS NOT NULL AND u.output_tokens IS NOT NULL
                    THEN (u.input_tokens::numeric * m.input_price_usd_per_1k_tokens::numeric
                         + u.output_tokens::numeric * m.output_price_usd_per_1k_tokens::numeric) / 1000
                    END) estimated_cost,
                COUNT(*) FILTER (WHERE m.input_price_usd_per_1k_tokens IS NULL
                    OR m.output_price_usd_per_1k_tokens IS NULL
                    OR m.input_price_usd_per_1k_tokens < 0 OR m.output_price_usd_per_1k_tokens < 0
                    OR u.input_tokens IS NULL OR u.output_tokens IS NULL) unpriced
            FROM historical.ai_usage_logs u
            LEFT JOIN historical.ai_models m ON m.model_id = u.model_id
            """ + w.sql() + " GROUP BY u.model_id, m.model_name ORDER BY requests DESC, u.model_id",
            (rs, n) -> new ModelCost(rs.getString("model_id"), rs.getString("name"),
                rs.getLong("requests"), rs.getLong("input_tokens"), rs.getLong("output_tokens"),
                rs.getBigDecimal("estimated_cost"), rs.getLong("unpriced")), w.params());
    }
}
