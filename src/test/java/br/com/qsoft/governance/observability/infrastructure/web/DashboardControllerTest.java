package br.com.qsoft.governance.observability.infrastructure.web;

import br.com.qsoft.governance.observability.application.DashboardQueryCommand;
import br.com.qsoft.governance.observability.application.QueryCostMetrics;
import br.com.qsoft.governance.observability.application.QueryUsageMetrics;
import br.com.qsoft.governance.observability.application.UsageMetricsSeries;
import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardControllerTest {

    private QueryUsageMetrics queryUsageMetrics;
    private QueryCostMetrics queryCostMetrics;
    private MockMvc mockMvc;

    private final TenantId tenantId = TenantId.random();
    private final UserId userId = UserId.random();

    @BeforeEach
    void setUp() {
        queryUsageMetrics = mock(QueryUsageMetrics.class);
        queryCostMetrics = mock(QueryCostMetrics.class);

        DashboardController controller = new DashboardController(queryUsageMetrics, queryCostMetrics);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ObservabilityApiExceptionHandler())
            .build();

        TenantContextHolder.set(new TenantContext(tenantId, userId));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void personalEndpointAlwaysUsesAuthenticatedUserNotClientParameter() throws Exception {
        when(queryCostMetrics.byGroup(any(), any())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/observability/metrics/me/costs")
                .param("userId", UUID.randomUUID().toString())
                .param("tenantId", UUID.randomUUID().toString()))
            .andExpect(status().isOk());
        var captor = org.mockito.ArgumentCaptor.forClass(DashboardQueryCommand.class);
        verify(queryCostMetrics).byGroup(captor.capture(), eq(UsageMetricsRepository.CostGroupBy.MODEL));
        assertThat(captor.getValue().targetUserId()).isEqualTo(userId.value());
        assertThat(captor.getValue().tenantId()).isEqualTo(tenantId);
        assertThat(captor.getValue().departmentId()).isNull();
    }

    @Test
    void acceptsIsoDatesAndForwardsInclusiveUtcDayBoundaries() throws Exception {
        when(queryCostMetrics.byGroup(any(DashboardQueryCommand.class), any())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/observability/metrics/costs")
                .param("from", "2026-10-01").param("to", "2026-10-21"))
            .andExpect(status().isOk());
        var captor = org.mockito.ArgumentCaptor.forClass(DashboardQueryCommand.class);
        verify(queryCostMetrics).byGroup(captor.capture(), any());
        assertThat(captor.getValue().from()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(captor.getValue().to()).isEqualTo(Instant.parse("2026-10-21T23:59:59.999999999Z"));
        mockMvc.perform(get("/api/v1/observability/metrics/usage").param("from", "2026-02-30"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deve_consultar_metricas_de_uso_com_sucesso() throws Exception {
        Instant now = Instant.now();
        UsageMetricsSeries series = new UsageMetricsSeries(
            "DAY", now.minusSeconds(86400), now, 5L,
            List.of(new UsageMetricsRepository.UsageSeriesPoint("2026-10-01", 100L))
        );

        when(queryUsageMetrics.series(any(DashboardQueryCommand.class))).thenReturn(series);

        mockMvc.perform(get("/api/v1/observability/metrics/usage")
                .param("granularity", "DAY"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.granularity").value("DAY"))
            .andExpect(jsonPath("$.activeUsers").value(5))
            .andExpect(jsonPath("$.series[0].period").value("2026-10-01"))
            .andExpect(jsonPath("$.series[0].requests").value(100));

        verify(queryUsageMetrics).series(any(DashboardQueryCommand.class));
    }

    @Test
    void deve_consultar_custos_por_departamento_com_sucesso() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UsageMetricsRepository.CostGroup cost = new UsageMetricsRepository.CostGroup(
            departmentId, 50L, 1000L, 2000L, new BigDecimal("0.050000"));

        when(queryCostMetrics.byGroup(any(DashboardQueryCommand.class), any()))
            .thenReturn(List.of(cost));

        mockMvc.perform(get("/api/v1/observability/metrics/costs")
                .param("departmentId", departmentId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].chave").value(departmentId.toString()))
            .andExpect(jsonPath("$[0].requisicoes").value(50))
            .andExpect(jsonPath("$[0].tokensEntrada").value(1000))
            .andExpect(jsonPath("$[0].tokensSaida").value(2000))
            .andExpect(jsonPath("$[0].custoEstimado").value(0.05));

        verify(queryCostMetrics).byGroup(any(DashboardQueryCommand.class),
            eq(UsageMetricsRepository.CostGroupBy.DEPARTMENT));
    }

    @Test
    void deve_encaminhar_groupBy_e_filtroDeUsuario() throws Exception {
        UUID userId = UUID.randomUUID();

        when(queryCostMetrics.byGroup(any(DashboardQueryCommand.class), any()))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/observability/metrics/costs")
                .param("groupBy", "USER")
                .param("userId", userId.toString()))
            .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<DashboardQueryCommand> captor =
            org.mockito.ArgumentCaptor.forClass(DashboardQueryCommand.class);
        verify(queryCostMetrics).byGroup(captor.capture(),
            eq(UsageMetricsRepository.CostGroupBy.USER));
        assertThat(captor.getValue().targetUserId()).isEqualTo(userId);
    }

    @Test
    void deve_retornar_unauthorized_quando_contexto_estiver_ausente_em_metrics() throws Exception {
        TenantContextHolder.clear();

        mockMvc.perform(get("/api/v1/observability/metrics/usage"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Não autenticado."));
    }

    // --- DTO Direct Tests ---

    @Test
    void deve_testar_records_de_response_de_metricas() {
        UUID departmentId = UUID.randomUUID();
        BigDecimal costVal = new BigDecimal("1.25");
        UsageMetricsRepository.CostGroup cost = new UsageMetricsRepository.CostGroup(
            departmentId, 10L, 500L, 1500L, costVal);

        CostGroupResponse costResponse = CostGroupResponse.from(cost);
        assertThat(costResponse.chave()).isEqualTo(departmentId);
        assertThat(costResponse.requisicoes()).isEqualTo(10L);
        assertThat(costResponse.tokensEntrada()).isEqualTo(500L);
        assertThat(costResponse.tokensSaida()).isEqualTo(1500L);
        assertThat(costResponse.custoEstimado()).isEqualTo(costVal);

        Instant now = Instant.now();
        UsageMetricsSeries series = new UsageMetricsSeries(
            "HOUR", now, now, 3L, List.of(new UsageMetricsRepository.UsageSeriesPoint("14:00", 25L))
        );
        UsageMetricsResponse metricsResponse = UsageMetricsResponse.from(series);
        assertThat(metricsResponse.granularity()).isEqualTo("HOUR");
        assertThat(metricsResponse.activeUsers()).isEqualTo(3L);
        assertThat(metricsResponse.series()).hasSize(1);
        assertThat(metricsResponse.series().get(0).period()).isEqualTo("14:00");
        assertThat(metricsResponse.series().get(0).requests()).isEqualTo(25L);
    }
}
