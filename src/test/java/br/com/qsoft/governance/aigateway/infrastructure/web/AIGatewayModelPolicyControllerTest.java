package br.com.qsoft.governance.aigateway.infrastructure.web;

import br.com.qsoft.governance.aigateway.application.ConfigureDepartmentPolicy;
import br.com.qsoft.governance.aigateway.application.ResolveModelForUser;
import br.com.qsoft.governance.aigateway.application.ResolvedModel;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AIGatewayModelPolicyControllerTest {

    private ConfigureDepartmentPolicy configureDepartmentPolicy;
    private ResolveModelForUser resolveModelForUser;
    private MockMvc mockMvc;

    private final TenantId tenantId = TenantId.random();
    private final UserId userId = UserId.random();

    @BeforeEach
    void setUp() {
        configureDepartmentPolicy = mock(ConfigureDepartmentPolicy.class);
        resolveModelForUser = mock(ResolveModelForUser.class);

        AIGatewayModelPolicyController controller = new AIGatewayModelPolicyController(
            configureDepartmentPolicy, resolveModelForUser);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new AIGatewayApiExceptionHandler())
            .build();

        TenantContextHolder.set(new TenantContext(tenantId, userId));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void deve_configurar_policy_do_departamento_com_sucesso() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();

        doNothing().when(configureDepartmentPolicy).configure(
            eq(tenantId), eq(departmentId), eq(Set.of(modelId)), eq(modelId));

        String payload = String.format("""
            {
              "allowedModelIds": ["%s"],
              "defaultModelId": "%s"
            }
            """, modelId, modelId);

        mockMvc.perform(put("/api/v1/ai-gateway/departments/" + departmentId + "/model-policy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isNoContent());

        verify(configureDepartmentPolicy).configure(tenantId, departmentId, Set.of(modelId), modelId);
    }

    @Test
    void deve_resolver_modelo_para_usuario_sem_modelo_solicitado() throws Exception {
        UUID modelId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        ResolvedModel resolvedModel = new ResolvedModel(modelId, "gpt-4o", providerId, "OpenAI");

        when(resolveModelForUser.resolve(tenantId, userId, null)).thenReturn(resolvedModel);

        mockMvc.perform(get("/api/v1/ai-gateway/models/permitido"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modelId").value(modelId.toString()))
            .andExpect(jsonPath("$.modelName").value("gpt-4o"))
            .andExpect(jsonPath("$.providerId").value(providerId.toString()))
            .andExpect(jsonPath("$.providerName").value("OpenAI"));

        verify(resolveModelForUser).resolve(tenantId, userId, null);
    }

    @Test
    void deve_resolver_modelo_para_usuario_com_modelo_solicitado() throws Exception {
        UUID requestedModelId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        ResolvedModel resolvedModel = new ResolvedModel(requestedModelId, "claude-3-5-sonnet", providerId, "Anthropic");

        when(resolveModelForUser.resolve(tenantId, userId, requestedModelId)).thenReturn(resolvedModel);

        mockMvc.perform(get("/api/v1/ai-gateway/models/permitido")
                .param("requestedModelId", requestedModelId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modelId").value(requestedModelId.toString()))
            .andExpect(jsonPath("$.modelName").value("claude-3-5-sonnet"))
            .andExpect(jsonPath("$.providerId").value(providerId.toString()))
            .andExpect(jsonPath("$.providerName").value("Anthropic"));

        verify(resolveModelForUser).resolve(tenantId, userId, requestedModelId);
    }

    @Test
    void deve_retornar_unauthorized_quando_contexto_de_tenant_nao_estiver_definido() throws Exception {
        TenantContextHolder.clear();

        mockMvc.perform(get("/api/v1/ai-gateway/models/permitido"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Não autenticado."));
    }

    // --- DTO Direct Tests ---

    @Test
    void deve_testar_records_request_e_response() {
        UUID defaultModelId = UUID.randomUUID();
        Set<UUID> allowed = Set.of(defaultModelId);
        ModelPolicyRequest policyRequest = new ModelPolicyRequest(allowed, defaultModelId);

        assertThat(policyRequest.allowedModelIds()).isEqualTo(allowed);
        assertThat(policyRequest.defaultModelId()).isEqualTo(defaultModelId);

        ResolvedModel resolved = new ResolvedModel(defaultModelId, "gpt-4", UUID.randomUUID(), "OpenAI");
        ResolvedModelResponse response = ResolvedModelResponse.from(resolved);

        assertThat(response.modelId()).isEqualTo(resolved.modelId());
        assertThat(response.modelName()).isEqualTo("gpt-4");
        assertThat(response.providerId()).isEqualTo(resolved.providerId());
        assertThat(response.providerName()).isEqualTo("OpenAI");
    }
}
