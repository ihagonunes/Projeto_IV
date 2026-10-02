package br.com.qsoft.governance.aigateway.infrastructure.web;

import br.com.qsoft.governance.aigateway.application.ChangeModelStatus;
import br.com.qsoft.governance.aigateway.application.ChangeModelStatusCommand;
import br.com.qsoft.governance.aigateway.application.CreateModel;
import br.com.qsoft.governance.aigateway.application.CreateModelCommand;
import br.com.qsoft.governance.aigateway.application.CreateProvider;
import br.com.qsoft.governance.aigateway.application.CreateProviderCommand;
import br.com.qsoft.governance.aigateway.application.ListModels;
import br.com.qsoft.governance.aigateway.application.ListProviders;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.aigateway.domain.model.ModelStatus;
import br.com.qsoft.governance.aigateway.domain.provider.Provider;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AIGatewayCatalogControllerTest {

    private CreateProvider createProvider;
    private ListProviders listProviders;
    private CreateModel createModel;
    private ListModels listModels;
    private ChangeModelStatus changeModelStatus;
    private MockMvc mockMvc;

    private final TenantId tenantId = TenantId.random();

    @BeforeEach
    void setUp() {
        createProvider = mock(CreateProvider.class);
        listProviders = mock(ListProviders.class);
        createModel = mock(CreateModel.class);
        listModels = mock(ListModels.class);
        changeModelStatus = mock(ChangeModelStatus.class);

        AIGatewayCatalogController controller = new AIGatewayCatalogController(
            createProvider, listProviders, createModel, listModels, changeModelStatus);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new AIGatewayApiExceptionHandler())
            .build();

        TenantContextHolder.set(new TenantContext(tenantId, null));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // --- Provider Endpoints ---

    @Test
    void deve_listar_providers_com_sucesso() throws Exception {
        Provider provider = Provider.create(UUID.randomUUID(), tenantId, "OpenAI");
        when(listProviders.list(tenantId)).thenReturn(List.of(provider));

        mockMvc.perform(get("/api/v1/ai-gateway/providers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(provider.id().toString()))
            .andExpect(jsonPath("$[0].name").value("OpenAI"))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        verify(listProviders).list(tenantId);
    }

    @Test
    void deve_criar_provider_com_sucesso() throws Exception {
        Provider provider = Provider.create(UUID.randomUUID(), tenantId, "Anthropic");
        when(createProvider.create(any(CreateProviderCommand.class))).thenReturn(provider);

        mockMvc.perform(post("/api/v1/ai-gateway/providers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Anthropic\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(provider.id().toString()))
            .andExpect(jsonPath("$.name").value("Anthropic"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(createProvider).create(any(CreateProviderCommand.class));
    }

    @Test
    void deve_retornar_bad_request_ao_criar_provider_com_nome_em_branco() throws Exception {
        mockMvc.perform(post("/api/v1/ai-gateway/providers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("name é obrigatório"));
    }

    // --- Model Endpoints ---

    @Test
    void deve_listar_models_com_sucesso() throws Exception {
        UUID providerId = UUID.randomUUID();
        Model model = Model.create(UUID.randomUUID(), tenantId, providerId, "gpt-4o",
            new BigDecimal("0.005"), new BigDecimal("0.015"));
        when(listModels.list(tenantId)).thenReturn(List.of(model));

        mockMvc.perform(get("/api/v1/ai-gateway/models"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(model.id().toString()))
            .andExpect(jsonPath("$[0].providerId").value(providerId.toString()))
            .andExpect(jsonPath("$[0].name").value("gpt-4o"))
            .andExpect(jsonPath("$[0].status").value("AVAILABLE"));

        verify(listModels).list(tenantId);
    }

    @Test
    void deve_criar_model_com_sucesso() throws Exception {
        UUID providerId = UUID.randomUUID();
        Model model = Model.create(UUID.randomUUID(), tenantId, providerId, "claude-3-5-sonnet",
            new BigDecimal("0.003"), new BigDecimal("0.015"));
        when(createModel.create(any(CreateModelCommand.class))).thenReturn(model);

        String payload = String.format("""
            {
              "providerId": "%s",
              "name": "claude-3-5-sonnet",
              "inputPricePer1k": 0.003,
              "outputPricePer1k": 0.015
            }
            """, providerId);

        mockMvc.perform(post("/api/v1/ai-gateway/models")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(model.id().toString()))
            .andExpect(jsonPath("$.name").value("claude-3-5-sonnet"))
            .andExpect(jsonPath("$.status").value("AVAILABLE"));

        verify(createModel).create(any(CreateModelCommand.class));
    }

    @Test
    void deve_retornar_bad_request_ao_criar_model_com_dados_invalidos() throws Exception {
        // providerId nulo, name em branco, preço negativo
        String payload = """
            {
              "providerId": null,
              "name": "",
              "inputPricePer1k": -1.0,
              "outputPricePer1k": 0.0
            }
            """;

        mockMvc.perform(post("/api/v1/ai-gateway/models")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deve_alterar_status_do_model_com_sucesso() throws Exception {
        UUID modelId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        Model model = Model.create(modelId, tenantId, providerId, "gpt-4o",
            new BigDecimal("0.005"), new BigDecimal("0.015")).disable();
        when(changeModelStatus.change(any(ChangeModelStatusCommand.class))).thenReturn(model);

        String payload = "{\"status\": \"DISABLED\"}";

        mockMvc.perform(patch("/api/v1/ai-gateway/models/" + modelId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(modelId.toString()))
            .andExpect(jsonPath("$.status").value("DISABLED"));

        verify(changeModelStatus).change(any(ChangeModelStatusCommand.class));
    }

    @Test
    void deve_retornar_bad_request_ao_alterar_status_sem_payload() throws Exception {
        UUID modelId = UUID.randomUUID();
        mockMvc.perform(patch("/api/v1/ai-gateway/models/" + modelId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deve_retornar_unauthorized_quando_contexto_de_tenant_nao_estiver_definido() throws Exception {
        TenantContextHolder.clear();

        mockMvc.perform(get("/api/v1/ai-gateway/providers"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Não autenticado."));
    }

    // --- DTO Direct Tests ---

    @Test
    void deve_testar_record_provider_response() {
        Provider provider = Provider.create(UUID.randomUUID(), tenantId, "OpenAI");
        ProviderResponse response = ProviderResponse.from(provider);

        assertThat(response.id()).isEqualTo(provider.id());
        assertThat(response.name()).isEqualTo("OpenAI");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.createdAt()).isEqualTo(provider.createdAt());
    }

    @Test
    void deve_testar_record_model_response() {
        UUID providerId = UUID.randomUUID();
        Model model = Model.create(UUID.randomUUID(), tenantId, providerId, "gpt-4o",
            new BigDecimal("0.005"), new BigDecimal("0.015"));
        ModelResponse response = ModelResponse.from(model);

        assertThat(response.id()).isEqualTo(model.id());
        assertThat(response.providerId()).isEqualTo(providerId);
        assertThat(response.name()).isEqualTo("gpt-4o");
        assertThat(response.status()).isEqualTo("AVAILABLE");
        assertThat(response.createdAt()).isEqualTo(model.createdAt());
    }

    @Test
    void deve_testar_records_request() {
        UUID providerId = UUID.randomUUID();
        ModelRequest modelRequest = new ModelRequest(providerId, "gpt-4", BigDecimal.ONE, BigDecimal.TEN);
        assertThat(modelRequest.providerId()).isEqualTo(providerId);
        assertThat(modelRequest.name()).isEqualTo("gpt-4");
        assertThat(modelRequest.inputPricePer1k()).isEqualTo(BigDecimal.ONE);
        assertThat(modelRequest.outputPricePer1k()).isEqualTo(BigDecimal.TEN);

        ProviderRequest providerRequest = new ProviderRequest("Provider A");
        assertThat(providerRequest.name()).isEqualTo("Provider A");

        ModelStatusRequest statusRequest = new ModelStatusRequest(ModelStatus.DISABLED);
        assertThat(statusRequest.status()).isEqualTo(ModelStatus.DISABLED);
    }
}
