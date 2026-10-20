package br.com.qsoft.governance.observability.infrastructure.query;

import br.com.qsoft.governance.observability.application.UsageVisibilityDeniedException;
import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adapter de leitura das métricas do dashboard (SPI-88/89) sobre
 * {@code observability_usage_records}.
 * <p>
 * Consulta <b>nativa</b> e <b>sempre tenant-aware</b> (ADR-03). O escopo vem do
 * {@link VisibilityScope} resolvido pelo IAM (ADR-36):
 * <ul>
 *   <li>{@code TenantWide} — sem cláusula extra (departamento pedido vira filtro);</li>
 *   <li>{@code Departments} — {@code (department_id IN (…) OR user_id = ?)};</li>
 *   <li>{@code SelfOnly} — {@code user_id = ?};</li>
 *   <li>{@code None} — 403 (defesa; o resolver já barra antes).</li>
 * </ul>
 * A granularidade é convertida para um literal whitelisted.
 */
@Component
public class UsageMetricsJdbcAdapter implements UsageMetricsRepository {

    private final JdbcTemplate jdbcTemplate;

    public UsageMetricsJdbcAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<UsageSeriesPoint> requestsByPeriod(UsageMetricsFilter filter) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT to_char(date_trunc('" + truncUnit(filter.granularity())
                + "', created_at), '" + truncFormat(filter.granularity())
                + "') AS period, COUNT(*) AS requests "
                + "FROM observability_usage_records WHERE tenant_id = ?");
        params.add(filter.tenantId().value());

        appendScopeAndPeriod(sql, params, filter);
        sql.append(" GROUP BY 1 ORDER BY 1");

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) ->
            new UsageSeriesPoint(rs.getString("period"), rs.getLong("requests")), params.toArray());
    }

    @Override
    public long activeUsers(UsageMetricsFilter filter) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(DISTINCT user_id) AS active_users "
                + "FROM observability_usage_records WHERE tenant_id = ?");
        params.add(filter.tenantId().value());

        appendScopeAndPeriod(sql, params, filter);

        Long total = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return total == null ? 0 : total;
    }

    @Override
    public List<DepartmentCost> costByDepartment(UsageMetricsFilter filter) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT department_id,
                   COUNT(*)                 AS requests,
                   COALESCE(SUM(input_tokens), 0)  AS input_tokens,
                   COALESCE(SUM(output_tokens), 0) AS output_tokens,
                   COALESCE(SUM(estimated_cost), 0) AS estimated_cost
            FROM observability_usage_records WHERE tenant_id = ?""");
        params.add(filter.tenantId().value());

        appendScopeAndPeriod(sql, params, filter);
        sql.append(" GROUP BY department_id ORDER BY estimated_cost DESC");

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            UUID departmentId = rs.getObject("department_id") == null
                ? null
                : (UUID) rs.getObject("department_id");
            return new DepartmentCost(
                departmentId,
                rs.getLong("requests"),
                rs.getLong("input_tokens"),
                rs.getLong("output_tokens"),
                rs.getBigDecimal("estimated_cost") == null ? BigDecimal.ZERO : rs.getBigDecimal("estimated_cost")
            );
        }, params.toArray());
    }

    private void appendScopeAndPeriod(StringBuilder sql, List<Object> params,
                                      UsageMetricsFilter filter) {
        appendVisibility(sql, params, filter.visibility(), filter.requestedDepartmentId());
        // Janela half-open [from, to): um registro exatamente no início do mês
        // seguinte não pertence ao mês consultado (SPEC-02 §3).
        sql.append(" AND created_at >= ? AND created_at < ?");
        params.add(Timestamp.from(filter.from()));
        params.add(Timestamp.from(filter.to()));
    }

    private void appendVisibility(StringBuilder sql, List<Object> params,
                                  VisibilityScope visibility, UUID requestedDepartmentId) {
        if (visibility instanceof VisibilityScope.TenantWide) {
            if (requestedDepartmentId != null) {
                sql.append(" AND department_id = ?");
                params.add(requestedDepartmentId);
            }
            return;
        }
        if (visibility instanceof VisibilityScope.Departments departments) {
            sql.append(" AND (department_id IN (")
                .append(String.join(",", Collections.nCopies(departments.ids().size(), "?")))
                .append(") OR user_id = ?)");
            params.addAll(departments.ids());
            params.add(departments.self().value());
            if (requestedDepartmentId != null) {
                sql.append(" AND department_id = ?");
                params.add(requestedDepartmentId);
            }
            return;
        }
        if (visibility instanceof VisibilityScope.SelfOnly selfOnly) {
            sql.append(" AND user_id = ?");
            params.add(selfOnly.self().value());
            return;
        }
        throw new UsageVisibilityDeniedException();
    }

    /** Literal whitelisted (nunca vem do cliente). */
    private static String truncUnit(Granularity granularity) {
        return switch (granularity) {
            case DAY -> "day";
            case WEEK -> "week";
            case MONTH -> "month";
        };
    }

    private static String truncFormat(Granularity granularity) {
        return switch (granularity) {
            case DAY -> "YYYY-MM-DD";
            case WEEK -> "IYYY-\"W\"IW";
            case MONTH -> "YYYY-MM";
        };
    }
}
