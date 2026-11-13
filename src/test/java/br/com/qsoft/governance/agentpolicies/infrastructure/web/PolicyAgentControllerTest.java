package br.com.qsoft.governance.agentpolicies.infrastructure.web;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.qsoft.governance.agentpolicies.application.AskPolicyAgent;
import br.com.qsoft.governance.agentpolicies.application.AskPolicyCommand;
import br.com.qsoft.governance.agentpolicies.application.KnowledgeHit;
import br.com.qsoft.governance.agentpolicies.application.PolicyAnswer;
import br.com.qsoft.governance.aigateway.api.completion.CompletionResult;
import br.com.qsoft.governance.aigateway.api.exception.AIAttemptNotRecordedException;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PolicyAgentControllerTest {

    private static final String QUESTION = "Qual é a política?";
    private final TenantId tenant = TenantId.random();
    private final UserId actor = UserId.random();
    private final AskPolicyAgent agent = mock(AskPolicyAgent.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PolicyAgentController(agent))
        .setControllerAdvice(new AgentApiExceptionHandler()).build();

    @BeforeEach
    void setContext() {
        TenantContextHolder.set(new TenantContext(tenant, actor));
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void mapsSourcesToolsAndOptionalReceipt(boolean completed) throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        CompletionResult receipt = completed ? new CompletionResult(requestId, "Resposta", List.of(),
            modelId, "Modelo", UUID.randomUUID(), "Provider", 5L, 6L, null, "INCONCLUSIVE", 12L) : null;
        when(agent.ask(new AskPolicyCommand(tenant, actor, QUESTION, modelId))).thenReturn(
            new PolicyAnswer("Resposta", List.of(new KnowledgeHit(UUID.randomUUID(), "Trecho",
                documentId, "Política", 2, 0.8)), List.of("buscar_politicas"), requestId, receipt));

        var result = mvc.perform(question("""
                {"pergunta":"%s","requestedModelId":"%s"}
                """.formatted(QUESTION, modelId)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.resposta").value("Resposta"))
            .andExpect(jsonPath("$.fontes[0].documentId").value(documentId.toString()))
            .andExpect(jsonPath("$.fontes[0].titulo").value("Política"))
            .andExpect(jsonPath("$.fontes[0].versao").value(2))
            .andExpect(jsonPath("$.fontes[0].trecho").value("Trecho"))
            .andExpect(jsonPath("$.ferramentasUsadas[0]").value("buscar_politicas"));
        if (completed) {
            result.andExpect(jsonPath("$.recibo.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.recibo.latenciaMs").value(12));
        } else {
            result.andExpect(jsonPath("$.recibo").value(nullValue()));
        }
    }

    @Test
    void rejectsBlankQuestionAndMissingContextBeforeAskingAgent() throws Exception {
        mvc.perform(question("{\"pergunta\":\" \"}")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("pergunta é obrigatória"));
        TenantContextHolder.clear();
        mvc.perform(question("{\"pergunta\":\"Qual é a política?\"}")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Não autenticado."));
        verifyNoInteractions(agent);
    }

    @Test
    void translatesFailureToRecordAttemptIntoServiceUnavailable() throws Exception {
        AIAttemptNotRecordedException failure = new AIAttemptNotRecordedException();
        when(agent.ask(any())).thenThrow(failure);

        mvc.perform(question("{\"pergunta\":\"Qual é a política?\"}")).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.message").value(failure.getMessage()));
    }

    @Test
    void returnsBadRequestForRejectedQuestionFromUseCase() throws Exception {
        when(agent.ask(any())).thenThrow(new IllegalArgumentException("Pergunta inválida."));

        mvc.perform(question("{\"pergunta\":\"Qual é a política?\"}")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Pergunta inválida."));
    }

    private static MockHttpServletRequestBuilder question(String body) {
        return post("/api/v1/agente-politicas/perguntas").contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
