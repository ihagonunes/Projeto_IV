package br.com.qsoft.governance.observability.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.observability.application.port.PeerConsumptionQuery.Average;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class PeerConsumptionJdbcAdapterTest {

    private static final YearMonth MONTH = YearMonth.of(2026, 7);
    private final TenantId tenant = TenantId.random();
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final PeerConsumptionJdbcAdapter adapter = new PeerConsumptionJdbcAdapter(jdbc);

    @Test
    void operationalBindsTenantDepartmentPeersAndUtcMonth() throws Exception {
        UUID department = UUID.randomUUID();
        List<UUID> peers = List.of(UUID.randomUUID());
        Map<String, ?> parameters = Map.of("tenant", tenant.value(), "department", department, "peers", peers,
            "start", Timestamp.from(Instant.parse("2026-07-01T00:00:00Z")),
            "end", Timestamp.from(Instant.parse("2026-08-01T00:00:00Z")));
        BigDecimal tokens = new BigDecimal("150");
        BigDecimal requests = new BigDecimal("2.5");
        stubRow(parameters, tokens, requests);

        assertThat(adapter.operational(tenant, department, peers, MONTH)).isEqualTo(new Average(tokens, requests));
        assertThat(sqlFor(parameters)).contains("COUNT(*) >= 10", "tenant_id = :tenant",
            "department_id = :department", "user_id IN (:peers)", "created_at >= :start", "created_at < :end");
    }

    @Test
    void historicalBindsCompanyAndLegacyIdsAndPreservesSuppressedAverages() throws Exception {
        List<String> departments = List.of("dept-1");
        List<String> peers = List.of("employee-1");
        Map<String, ?> parameters = Map.of("tenant", tenant.toString(), "departments", departments, "peers", peers,
            "start", "2026-07-01", "end", "2026-08-01");
        stubRow(parameters, null, null);

        assertThat(adapter.historical(tenant, departments, peers, MONTH)).isEqualTo(new Average(null, null));
        assertThat(sqlFor(parameters)).contains("COUNT(*) >= 10", "company_id = :tenant",
            "department_id IN (:departments)", "employee_id IN (:peers)", "request_count > 0");
    }

    private void stubRow(Map<String, ?> parameters, BigDecimal tokens, BigDecimal requests) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getBigDecimal("average_tokens")).thenReturn(tokens);
        when(row.getBigDecimal("average_requests")).thenReturn(requests);
        when(jdbc.queryForObject(anyString(), eq(parameters), ArgumentMatchers.<RowMapper<Average>>any()))
            .thenAnswer(invocation -> invocation.<RowMapper<Average>>getArgument(2).mapRow(row, 0));
    }

    private String sqlFor(Map<String, ?> parameters) {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), eq(parameters), ArgumentMatchers.<RowMapper<Average>>any());
        return sql.getValue();
    }
}
