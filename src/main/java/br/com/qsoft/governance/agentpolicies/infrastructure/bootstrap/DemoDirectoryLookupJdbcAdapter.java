package br.com.qsoft.governance.agentpolicies.infrastructure.bootstrap;

import br.com.qsoft.governance.agentpolicies.application.port.DemoDirectoryLookup;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Lookup de tenant/depto exclusivo do seeder demo (leitura controlada). */
@Component
public class DemoDirectoryLookupJdbcAdapter implements DemoDirectoryLookup {

    private final JdbcTemplate jdbcTemplate;

    public DemoDirectoryLookupJdbcAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UUID> findTenantIdBySlug(String slug) {
        List<UUID> ids = jdbcTemplate.query(
            "SELECT id FROM iam_tenants WHERE slug = ?", (rs, rowNum) ->
                rs.getObject("id", UUID.class), slug);
        return ids.stream().findFirst();
    }

    @Override
    public Optional<UUID> findDepartmentIdByName(UUID tenantId, String name) {
        List<UUID> ids = jdbcTemplate.query("""
                SELECT id FROM department_departments
                 WHERE tenant_id = ? AND name = ? AND status = 'ACTIVE'
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), tenantId, name);
        return ids.stream().findFirst();
    }
}
