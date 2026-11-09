package br.com.qsoft.governance.observability.infrastructure.query;

import br.com.qsoft.governance.observability.application.port.PeerConsumptionQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.sql.Timestamp;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PeerConsumptionJdbcAdapter implements PeerConsumptionQuery {
    private final NamedParameterJdbcTemplate jdbc;

    public PeerConsumptionJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Average operational(TenantId tenant, UUID department, Collection<UUID> peers, YearMonth month) {
        return jdbc.queryForObject("""
            SELECT CASE WHEN COUNT(*) >= 10 THEN ROUND(AVG(tokens)) END AS average_tokens,
                   CASE WHEN COUNT(*) >= 10 THEN AVG(requests) END AS average_requests
            FROM (
                SELECT SUM(input_tokens::numeric + output_tokens::numeric) AS tokens, COUNT(*) AS requests
                FROM observability_usage_records
                WHERE tenant_id = :tenant AND department_id = :department AND user_id IN (:peers)
                    AND created_at >= :start AND created_at < :end
                GROUP BY user_id
            ) peers_with_usage
            """, Map.of("tenant", tenant.value(), "department", department, "peers", peers,
                "start", Timestamp.from(month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC)),
                "end", Timestamp.from(month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC))),
            (rs, row) -> new Average(rs.getBigDecimal("average_tokens"), rs.getBigDecimal("average_requests")));
    }

    @Override
    public Average historical(TenantId tenant, Collection<String> departments, Collection<String> peers,
                                 YearMonth month) {
        return jdbc.queryForObject("""
            SELECT CASE WHEN COUNT(*) >= 10 THEN ROUND(AVG(tokens)) END AS average_tokens,
                   CASE WHEN COUNT(*) >= 10 THEN AVG(requests) END AS average_requests
            FROM (
                SELECT SUM(input_tokens::numeric + output_tokens::numeric) AS tokens, SUM(request_count) AS requests
                FROM historical.ai_usage_logs
                WHERE company_id = :tenant AND department_id IN (:departments) AND employee_id IN (:peers)
                    AND "timestamp" >= :start AND "timestamp" < :end AND request_count > 0
                GROUP BY employee_id
                HAVING COUNT(input_tokens::numeric + output_tokens::numeric) > 0
            ) peers_with_usage
            """, Map.of("tenant", tenant.value().toString(), "departments", departments, "peers", peers,
                "start", month.atDay(1).toString(), "end", month.plusMonths(1).atDay(1).toString()),
            (rs, row) -> new Average(rs.getBigDecimal("average_tokens"), rs.getBigDecimal("average_requests")));
    }
}
