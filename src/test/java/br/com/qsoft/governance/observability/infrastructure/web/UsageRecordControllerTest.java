package br.com.qsoft.governance.observability.infrastructure.web;

import br.com.qsoft.governance.observability.application.QueryUsage;
import br.com.qsoft.governance.observability.application.QueryUsageCommand;
import br.com.qsoft.governance.observability.domain.UsageRecord;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UsageRecordControllerTest {

    private QueryUsage queryUsage;
    private MockMvc mockMvc;

    private final TenantId tenantId = TenantId.random();
    private final UserId userId = UserId.random();

    @BeforeEach
    void setUp() {
        queryUsage = mock(QueryUsage.class);

        UsageRecordController controller = new UsageRecordController(queryUsage);

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
    void deve_consultar_registros_de_uso_com_paginacao() throws Exception {
        UUID recordId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        BigDecimal cost = new BigDecimal("0.002000");
        Instant now = Instant.now();

        UsageRecord record = UsageRecord.restore(
            recordId, requestId, tenantId, departmentId, userId.value(),
            modelId, providerId, 100L, 200L, cost, 150L, "SUCCESS", now
        );

        PageResult<UsageRecord> pageResult = new PageResult<>(List.of(record), 1, 0, 20);

        when(queryUsage.query(any(QueryUsageCommand.class))).thenReturn(pageResult);

        mockMvc.perform(get("/api/v1/observability/usage")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(recordId.toString()))
            .andExpect(jsonPath("$.content[0].requestId").value(requestId.toString()))
            .andExpect(jsonPath("$.content[0].status").value("SUCCESS"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20));

        verify(queryUsage).query(any(QueryUsageCommand.class));
    }

    @Test
    void deve_retornar_unauthorized_quando_contexto_estiver_ausente() throws Exception {
        TenantContextHolder.clear();

        mockMvc.perform(get("/api/v1/observability/usage"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Não autenticado."));
    }

    // --- DTO Direct Tests ---

    @Test
    void deve_testar_usage_page_e_record_response() {
        UUID recordId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        BigDecimal cost = new BigDecimal("0.002000");
        Instant now = Instant.now();

        UsageRecord record = UsageRecord.restore(
            recordId, requestId, tenantId, departmentId, userId.value(),
            modelId, providerId, 100L, 200L, cost, 150L, "SUCCESS", now
        );

        UsageRecordResponse response = UsageRecordResponse.from(record);
        assertThat(response.id()).isEqualTo(recordId);
        assertThat(response.requestId()).isEqualTo(requestId);
        assertThat(response.departmentId()).isEqualTo(departmentId);
        assertThat(response.userId()).isEqualTo(userId.value());
        assertThat(response.modelId()).isEqualTo(modelId);
        assertThat(response.providerId()).isEqualTo(providerId);
        assertThat(response.inputTokens()).isEqualTo(100L);
        assertThat(response.outputTokens()).isEqualTo(200L);
        assertThat(response.estimatedCost()).isEqualTo(cost);
        assertThat(response.latencyMs()).isEqualTo(150L);
        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.createdAt()).isEqualTo(now);

        PageResult<UsageRecord> pageResult = new PageResult<>(List.of(record), 1, 0, 20);
        UsagePageResponse pageResponse = UsagePageResponse.from(pageResult);

        assertThat(pageResponse.content()).hasSize(1);
        assertThat(pageResponse.totalElements()).isEqualTo(1);
        assertThat(pageResponse.page()).isEqualTo(0);
        assertThat(pageResponse.size()).isEqualTo(20);
        assertThat(pageResponse.totalPages()).isEqualTo(1);
    }
}
