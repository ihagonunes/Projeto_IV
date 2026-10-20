package br.com.qsoft.governance.observability.infrastructure.query;

import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository;
import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository.DepartmentCost;
import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository.Granularity;
import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository.UsageMetricsFilter;
import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository.UsageSeriesPoint;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageMetricsJdbcAdapterTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private UsageMetricsJdbcAdapter adapter;

    private final TenantId tenantId = TenantId.random();
    private final Instant from = Instant.now().minusSeconds(86400);
    private final Instant to = Instant.now();

    // --- requestsByPeriod Tests ---

    @Test
    void deve_buscar_requisicoes_por_periodo_dia() {
        UsageMetricsFilter filter = new UsageMetricsFilter(tenantId, new br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope.TenantWide(), null, from, to, Granularity.DAY);
        UsageSeriesPoint point = new UsageSeriesPoint("2026-10-01", 50L);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(point));

        List<UsageSeriesPoint> result = adapter.requestsByPeriod(filter);

        assertThat(result).containsExactly(point);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sqlCaptor.getValue()).contains("date_trunc('day'");
        assertThat(sqlCaptor.getValue()).contains("YYYY-MM-DD");
    }

    @Test
    void deve_buscar_requisicoes_por_periodo_semana() {
        UsageMetricsFilter filter = new UsageMetricsFilter(tenantId, new br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope.TenantWide(), null, from, to, Granularity.WEEK);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of());

        adapter.requestsByPeriod(filter);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sqlCaptor.getValue()).contains("date_trunc('week'");
        assertThat(sqlCaptor.getValue()).contains("IYYY-\"W\"IW");
    }

    @Test
    void deve_buscar_requisicoes_por_periodo_mes() {
        UsageMetricsFilter filter = new UsageMetricsFilter(tenantId, new br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope.TenantWide(), null, from, to, Granularity.MONTH);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of());

        adapter.requestsByPeriod(filter);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sqlCaptor.getValue()).contains("date_trunc('month'");
        assertThat(sqlCaptor.getValue()).contains("YYYY-MM");
    }

    @Test
    void deve_aplicar_visibilidade_departments_com_uniao_e_self() {
        UUID self = UUID.randomUUID();
        UUID deptA = UUID.randomUUID();
        UUID deptB = UUID.randomUUID();
        UsageMetricsFilter filter = new UsageMetricsFilter(tenantId,
            new br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope.Departments(
                java.util.Set.of(deptA, deptB),
                br.com.qsoft.governance.sharedkernel.id.UserId.of(self)),
            null, from, to, Granularity.DAY);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of());

        adapter.requestsByPeriod(filter);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), paramsCaptor.capture());

        assertThat(sqlCaptor.getValue()).contains("AND (department_id IN (?,?) OR user_id = ?)");
        assertThat(paramsCaptor.getValue()).contains(deptA, deptB, self);
    }

    @Test
    void deve_aplicar_visibilidade_selfOnly() {
        UUID self = UUID.randomUUID();
        UsageMetricsFilter filter = new UsageMetricsFilter(tenantId,
            new br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope.SelfOnly(
                br.com.qsoft.governance.sharedkernel.id.UserId.of(self)),
            null, from, to, Granularity.DAY);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of());

        adapter.requestsByPeriod(filter);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), paramsCaptor.capture());

        assertThat(sqlCaptor.getValue()).contains("AND user_id = ?");
        assertThat(paramsCaptor.getValue()).contains(self);
    }

    @Test
    void deve_testar_row_mapper_do_requests_by_period() throws SQLException {
        UsageMetricsFilter filter = new UsageMetricsFilter(tenantId, new br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope.TenantWide(), null, from, to, Granularity.DAY);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenAnswer(invocation -> {
                RowMapper<UsageSeriesPoint> rowMapper = invocation.getArgument(1);
                ResultSet rs = mock(ResultSet.class);
                when(rs.getString("period")).thenReturn("2026-10-01");
                when(rs.getLong("requests")).thenReturn(120L);
                return List.of(rowMapper.mapRow(rs, 1));
            });

        List<UsageSeriesPoint> result = adapter.requestsByPeriod(filter);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).period()).isEqualTo("2026-10-01");
        assertThat(result.get(0).requests()).isEqualTo(120L);
    }

    // --- activeUsers Tests ---

    @Test
    void deve_contar_usuarios_ativos_retornando_valor() {
        UsageMetricsFilter filter = new UsageMetricsFilter(tenantId, new br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope.TenantWide(), null, from, to, Granularity.DAY);

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
            .thenReturn(15L);

        long active = adapter.activeUsers(filter);

        assertThat(active).isEqualTo(15L);
    }

    @Test
    void deve_retornar_zero_quando_contagem_de_usuarios_ativos_for_nula() {
        UsageMetricsFilter filter = new UsageMetricsFilter(tenantId, new br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope.TenantWide(), null, from, to, Granularity.DAY);

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
            .thenReturn(null);

        long active = adapter.activeUsers(filter);

        assertThat(active).isEqualTo(0L);
    }

    // --- costByDepartment Tests ---

    @Test
    void deve_buscar_custo_por_departamento_mapeando_valores() throws SQLException {
        UUID deptId = UUID.randomUUID();
        BigDecimal costVal = new BigDecimal("1.500000");
        UsageMetricsFilter filter = new UsageMetricsFilter(tenantId, new br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope.TenantWide(), null, from, to, Granularity.DAY);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenAnswer(invocation -> {
                RowMapper<UsageMetricsRepository.CostGroup> rowMapper = invocation.getArgument(1);
                ResultSet rs = mock(ResultSet.class);
                when(rs.getObject("group_key")).thenReturn(deptId);
                when(rs.getLong("requests")).thenReturn(25L);
                when(rs.getLong("input_tokens")).thenReturn(1000L);
                when(rs.getLong("output_tokens")).thenReturn(2000L);
                when(rs.getBigDecimal("estimated_cost")).thenReturn(costVal);
                return List.of(rowMapper.mapRow(rs, 1));
            });

        List<DepartmentCost> costs = adapter.costByDepartment(filter);

        assertThat(costs).hasSize(1);
        assertThat(costs.get(0).departmentId()).isEqualTo(deptId);
        assertThat(costs.get(0).requests()).isEqualTo(25L);
        assertThat(costs.get(0).inputTokens()).isEqualTo(1000L);
        assertThat(costs.get(0).outputTokens()).isEqualTo(2000L);
        assertThat(costs.get(0).estimatedCost()).isEqualTo(costVal);
    }

    @Test
    void deve_buscar_custo_por_departamento_tratando_valores_nulos_no_resultset() throws SQLException {
        UsageMetricsFilter filter = new UsageMetricsFilter(tenantId, new br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope.TenantWide(), null, from, to, Granularity.DAY);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenAnswer(invocation -> {
                RowMapper<UsageMetricsRepository.CostGroup> rowMapper = invocation.getArgument(1);
                ResultSet rs = mock(ResultSet.class);
                when(rs.getObject("group_key")).thenReturn(null);
                when(rs.getLong("requests")).thenReturn(0L);
                when(rs.getLong("input_tokens")).thenReturn(0L);
                when(rs.getLong("output_tokens")).thenReturn(0L);
                when(rs.getBigDecimal("estimated_cost")).thenReturn(null);
                return List.of(rowMapper.mapRow(rs, 1));
            });

        List<DepartmentCost> costs = adapter.costByDepartment(filter);

        assertThat(costs).hasSize(1);
        assertThat(costs.get(0).departmentId()).isNull();
        assertThat(costs.get(0).estimatedCost()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void janelaDeCustoEhHalfOpenParaNaoVazarOMesSeguinte() {
        UsageMetricsFilter filter = new UsageMetricsFilter(tenantId,
            new br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope.TenantWide(),
            null, from, to, Granularity.MONTH);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of());

        adapter.costByDepartment(filter);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sqlCaptor.getValue()).contains("created_at >= ? AND created_at < ?");
    }

    @Test
    void deve_agruparPorModeloEUsuarioComColunasWhitelisted() {
        UsageMetricsFilter filter = new UsageMetricsFilter(tenantId,
            new br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope.TenantWide(),
            null, from, to, Granularity.MONTH);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of());

        adapter.costGroups(filter, UsageMetricsRepository.CostGroupBy.MODEL);
        adapter.costGroups(filter, UsageMetricsRepository.CostGroupBy.USER);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2))
            .query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));

        assertThat(sqlCaptor.getAllValues().get(0))
            .contains("model_id").contains("GROUP BY model_id");
        assertThat(sqlCaptor.getAllValues().get(1))
            .contains("user_id").contains("GROUP BY user_id");
    }

    @Test
    void deve_aplicarFiltrosDeModeloEUsuario() {
        UUID modelId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UsageMetricsFilter filter = new UsageMetricsFilter(tenantId,
            new br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope.TenantWide(),
            null, modelId, targetUserId, from, to, Granularity.DAY);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of());

        adapter.requestsByPeriod(filter);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), paramsCaptor.capture());

        assertThat(sqlCaptor.getValue()).contains("AND model_id = ?").contains("AND user_id = ?");
        assertThat(paramsCaptor.getValue()).contains(modelId, targetUserId);
    }
}
