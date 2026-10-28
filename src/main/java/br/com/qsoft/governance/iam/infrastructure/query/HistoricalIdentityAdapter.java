package br.com.qsoft.governance.iam.infrastructure.query;

import br.com.qsoft.governance.iam.api.query.HistoricalIdentityQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

@Component
public class HistoricalIdentityAdapter implements HistoricalIdentityQuery {

    private static final int BATCH_SIZE = 5_000;

    private final JdbcTemplate jdbc;

    public HistoricalIdentityAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> employeeId(TenantId tenantId, UserId userId) {
        return jdbc.query("""
            SELECT l.legacy_employee_id FROM iam_legacy_user_links l
            JOIN app_user u ON u.tenant_id=l.tenant_id AND u.id=l.user_id
            WHERE l.tenant_id=? AND l.user_id=?
            """, (rs, n) -> rs.getString(1), tenantId.value(), userId.value()).stream().findFirst();
    }

    @Override
    public Map<UserId, String> employeeIds(TenantId tenantId, Set<UserId> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<UserId, String> links = new LinkedHashMap<>();
        List<UserId> ids = List.copyOf(userIds);
        for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
            List<UserId> batch = ids.subList(start, Math.min(start + BATCH_SIZE, ids.size()));
            String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
            List<Object> params = new ArrayList<>(batch.size() + 1);
            params.add(tenantId.value());
            batch.forEach(id -> params.add(id.value()));

            RowCallbackHandler handler = rs -> links.put(
                UserId.of(rs.getObject("user_id", UUID.class)),
                rs.getString("legacy_employee_id"));
            jdbc.query("""
                SELECT l.user_id, l.legacy_employee_id
                  FROM iam_legacy_user_links l
                  JOIN app_user u ON u.tenant_id = l.tenant_id AND u.id = l.user_id
                 WHERE l.tenant_id = ? AND l.user_id IN (%s)
                """.formatted(placeholders), handler, params.toArray());
        }
        return links;
    }
}
