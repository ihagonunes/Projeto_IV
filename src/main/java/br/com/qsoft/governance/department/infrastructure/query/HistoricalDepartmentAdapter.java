package br.com.qsoft.governance.department.infrastructure.query;

import br.com.qsoft.governance.department.api.query.HistoricalDepartmentQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class HistoricalDepartmentAdapter implements HistoricalDepartmentQuery {
    private final JdbcTemplate jdbc;
    public HistoricalDepartmentAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override
    public Set<String> legacyIds(TenantId tenant, Set<UUID> ids) {
        if (ids.isEmpty()) return Set.of();
        var params = new ArrayList<Object>(); params.add(tenant.value()); params.addAll(ids);
        return new HashSet<>(jdbc.query("""
            SELECT l.legacy_department_id FROM department_legacy_links l
            JOIN department_departments d ON d.tenant_id=l.tenant_id AND d.id=l.department_id
            WHERE l.tenant_id=? AND d.status='ACTIVE' AND l.department_id IN (
            """ + String.join(",", Collections.nCopies(ids.size(), "?")) + ")",
            (rs, n) -> rs.getString(1), params.toArray()));
    }
}
