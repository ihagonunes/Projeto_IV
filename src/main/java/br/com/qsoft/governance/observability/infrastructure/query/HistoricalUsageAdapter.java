package br.com.qsoft.governance.observability.infrastructure.query;

import br.com.qsoft.governance.observability.application.port.HistoricalUsageQuery;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adapter do read model do histórico (SPI-75). Consulta <b>nativa</b> sobre
 * {@code historical.ai_usage_logs} — que <b>não</b> pertence a nenhum módulo
 * operacional e não tem FK (ADR-26).
 * <p>
 * A consulta é <b>sempre tenant-aware</b>: o filtro explícito por
 * {@code company_id = :tenantId} é obrigatório (ADR-03) — o loader reescreve
 * {@code company_id} para o {@code tenant_id} da plataforma.
 */
@Component
public class HistoricalUsageAdapter implements HistoricalUsageQuery {

    private final JdbcTemplate jdbcTemplate;

    public HistoricalUsageAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public HistoricalUsageSummary summarize(HistoricalUsageFilter filter) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT COALESCE(SUM(request_count), 0)  AS requests,
                   COALESCE(SUM(input_tokens), 0)   AS input_tokens,
                   COALESCE(SUM(output_tokens), 0)  AS output_tokens,
                   COALESCE(AVG(latency_ms), 0)     AS avg_latency
            FROM historical.ai_usage_logs
            WHERE company_id = ?
            """);
        params.add(filter.tenantId().value().toString());

        if (filter.departmentId() != null) {
            sql.append(" AND department_id = ?");
            params.add(filter.departmentId());
        }
        if (filter.from() != null) {
            sql.append(" AND \"timestamp\" >= ?");
            params.add(filter.from().toString());
        }
        if (filter.to() != null) {
            sql.append(" AND \"timestamp\" <= ?");
            params.add(filter.to().toString() + "T23:59:59");
        }

        return jdbcTemplate.queryForObject(sql.toString(), (rs, rowNum) ->
            new HistoricalUsageSummary(
                rs.getLong("requests"),
                rs.getLong("input_tokens"),
                rs.getLong("output_tokens"),
                rs.getDouble("avg_latency")
            ), params.toArray());
    }
}
