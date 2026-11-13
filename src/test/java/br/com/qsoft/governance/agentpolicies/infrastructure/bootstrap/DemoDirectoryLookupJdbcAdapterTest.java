package br.com.qsoft.governance.agentpolicies.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class DemoDirectoryLookupJdbcAdapterTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final DemoDirectoryLookupJdbcAdapter directory = new DemoDirectoryLookupJdbcAdapter(jdbc);

    @Test
    void findsTenantBySlug() throws Exception {
        UUID tenant = UUID.randomUUID();
        ResultSet row = mock(ResultSet.class);
        when(row.getObject("id", UUID.class)).thenReturn(tenant);
        when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<UUID>>any(), eq("qsoft")))
            .thenAnswer(invocation -> List.of(invocation.<RowMapper<UUID>>getArgument(1).mapRow(row, 0)));

        assertThat(directory.findTenantIdBySlug("qsoft")).contains(tenant);
        assertThat(directory.findTenantIdBySlug("missing")).isEmpty();
        verify(jdbc).query(eq("SELECT id FROM iam_tenants WHERE slug = ?"),
            ArgumentMatchers.<RowMapper<UUID>>any(), eq("qsoft"));
    }

    @Test
    void findsOnlyActiveDepartmentWithinTenant() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID department = UUID.randomUUID();
        ResultSet row = mock(ResultSet.class);
        when(row.getObject("id", UUID.class)).thenReturn(department);
        when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<UUID>>any(), eq(tenant), eq("Marketing")))
            .thenAnswer(invocation -> List.of(invocation.<RowMapper<UUID>>getArgument(1).mapRow(row, 0)));

        assertThat(directory.findDepartmentIdByName(tenant, "Marketing")).contains(department);
        assertThat(directory.findDepartmentIdByName(UUID.randomUUID(), "Marketing")).isEmpty();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), ArgumentMatchers.<RowMapper<UUID>>any(), eq(tenant), eq("Marketing"));
        assertThat(sql.getValue()).contains("tenant_id = ?", "name = ?", "status = 'ACTIVE'");
    }
}
