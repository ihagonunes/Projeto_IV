package br.com.qsoft.governance.observability.infrastructure.query;

import br.com.qsoft.governance.observability.application.port.HistoricalUsageQuery.HistoricalUsageFilter;
import br.com.qsoft.governance.observability.application.port.HistoricalUsageQuery.HistoricalUsageSummary;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricalUsageAdapterTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private HistoricalUsageAdapter adapter;

    private final TenantId tenantId = TenantId.random();

    @Test
    void deve_sumarizar_historico_com_filtro_basico() {
        HistoricalUsageFilter filter = new HistoricalUsageFilter(tenantId, null, null, null);
        HistoricalUsageSummary expectedSummary = new HistoricalUsageSummary(100L, 5000L, 10000L, 250.5);

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(expectedSummary);

        HistoricalUsageSummary summary = adapter.summarize(filter);

        assertThat(summary).isEqualTo(expectedSummary);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), any(RowMapper.class), paramsCaptor.capture());

        assertThat(sqlCaptor.getValue()).contains("WHERE company_id = ?");
        assertThat(sqlCaptor.getValue()).doesNotContain("AND department_id = ?");
        assertThat(paramsCaptor.getValue()).containsExactly(tenantId.value().toString());
    }

    @Test
    void deve_sumarizar_historico_com_filtro_completo() {
        String deptId = UUID.randomUUID().toString();
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        HistoricalUsageFilter filter = new HistoricalUsageFilter(tenantId, deptId, from, to);
        HistoricalUsageSummary expectedSummary = new HistoricalUsageSummary(50L, 2000L, 4000L, 180.0);

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(expectedSummary);

        HistoricalUsageSummary summary = adapter.summarize(filter);

        assertThat(summary).isEqualTo(expectedSummary);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), any(RowMapper.class), paramsCaptor.capture());

        assertThat(sqlCaptor.getValue()).contains("AND department_id = ?");
        assertThat(sqlCaptor.getValue()).contains("AND \"timestamp\" >= ?");
        assertThat(sqlCaptor.getValue()).contains("AND \"timestamp\" <= ?");
        assertThat(paramsCaptor.getValue()).containsExactly(
            tenantId.value().toString(),
            deptId,
            "2026-01-01",
            "2026-01-31T23:59:59"
        );
    }

    @Test
    void deve_testar_row_mapper_do_summarize() throws SQLException {
        HistoricalUsageFilter filter = new HistoricalUsageFilter(tenantId, null, null, null);

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenAnswer(invocation -> {
                RowMapper<HistoricalUsageSummary> rowMapper = invocation.getArgument(1);
                ResultSet rs = mock(ResultSet.class);
                when(rs.getLong("requests")).thenReturn(10L);
                when(rs.getLong("input_tokens")).thenReturn(100L);
                when(rs.getLong("output_tokens")).thenReturn(200L);
                when(rs.getDouble("avg_latency")).thenReturn(150.0);
                return rowMapper.mapRow(rs, 1);
            });

        HistoricalUsageSummary summary = adapter.summarize(filter);

        assertThat(summary.requests()).isEqualTo(10L);
        assertThat(summary.inputTokens()).isEqualTo(100L);
        assertThat(summary.outputTokens()).isEqualTo(200L);
        assertThat(summary.averageLatencyMs()).isEqualTo(150.0);
    }
}
