package br.com.qsoft.governance.observability.infrastructure.query;

import br.com.qsoft.governance.observability.api.query.UsageActivityQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

/**
 * Adapter do contrato {@link UsageActivityQuery} sobre
 * {@code observability_usage_records}.
 *
 * <p>Consulta nativa e <b>sempre tenant-aware</b> (ADR-03). O dia é o dia UTC de
 * {@code created_at}; a janela é half-open em UTC
 * ({@code [from 00:00Z, to+1 00:00Z)}) para incluir as duas datas pedidas.
 */
@Component
public class UsageActivityJdbcAdapter implements UsageActivityQuery {

    private static final int BATCH_SIZE = 5_000;

    private final JdbcTemplate jdbcTemplate;

    public UsageActivityJdbcAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<UserId, Set<LocalDate>> activeDays(TenantId tenantId, Set<UserId> userIds,
                                                  LocalDate from, LocalDate to) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from não pode ser posterior a to");
        }
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        Timestamp fromInclusive = Timestamp.from(
            from.atStartOfDay(ZoneOffset.UTC).toInstant());
        Timestamp toExclusive = Timestamp.from(
            to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());

        Map<UserId, Set<LocalDate>> result = new LinkedHashMap<>();
        List<UserId> ids = List.copyOf(userIds);
        for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
            List<UserId> batch = ids.subList(start, Math.min(start + BATCH_SIZE, ids.size()));
            String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
            List<Object> params = new ArrayList<>(batch.size() + 3);
            params.add(tenantId.value());
            batch.forEach(id -> params.add(id.value()));
            params.add(fromInclusive);
            params.add(toExclusive);

            RowCallbackHandler handler = rs -> {
                UserId userId = UserId.of(rs.getObject("user_id", UUID.class));
                LocalDate day = rs.getDate("active_day").toLocalDate();
                result.computeIfAbsent(userId, ignored -> new TreeSet<>()).add(day);
            };
            jdbcTemplate.query("""
                SELECT user_id, (created_at AT TIME ZONE 'UTC')::date AS active_day
                  FROM observability_usage_records
                 WHERE tenant_id = ?
                   AND user_id IN (%s)
                   AND created_at >= ?
                   AND created_at < ?
                 GROUP BY user_id, active_day
                """.formatted(placeholders), handler, params.toArray());
        }
        return result;
    }
}
