package br.com.qsoft.governance.iam.infrastructure.query;

import br.com.qsoft.governance.iam.api.query.HistoricalIdentityQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class HistoricalIdentityAdapter implements HistoricalIdentityQuery {
    private final JdbcTemplate jdbc;
    public HistoricalIdentityAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override
    public Optional<String> employeeId(TenantId tenantId, UserId userId) {
        return jdbc.query("""
            SELECT l.legacy_employee_id FROM iam_legacy_user_links l
            JOIN app_user u ON u.tenant_id=l.tenant_id AND u.id=l.user_id
            WHERE l.tenant_id=? AND l.user_id=?
            """, (rs, n) -> rs.getString(1), tenantId.value(), userId.value()).stream().findFirst();
    }
}
