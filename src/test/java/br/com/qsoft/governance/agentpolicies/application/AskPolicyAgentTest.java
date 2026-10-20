package br.com.qsoft.governance.agentpolicies.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.aigateway.api.completion.AICompletionService;
import br.com.qsoft.governance.aigateway.api.completion.CompletionCommand;
import br.com.qsoft.governance.aigateway.api.completion.CompletionResult;
import br.com.qsoft.governance.aigateway.api.completion.ToolCall;
import br.com.qsoft.governance.aigateway.api.exception.AIProviderFailureException;
import br.com.qsoft.governance.aigateway.api.model.ResolvedModelInfo;
import br.com.qsoft.governance.aigateway.api.query.ModelResolutionQuery;
import br.com.qsoft.governance.agentpolicies.application.port.AgentExecutionRepository;
import br.com.qsoft.governance.agentpolicies.domain.AgentExecution;
import br.com.qsoft.governance.agentpolicies.domain.AgentExecutionStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Orquestração: sem trechos não chama o LLM, ferramentas e falhas. */
class AskPolicyAgentTest {

    private final TenantId tenantId = TenantId.random();
    private final UserId userId = UserId.random();
    private final UUID echoModel = UUID.randomUUID();

    private final KnowledgeAccessService knowledge = mock(KnowledgeAccessService.class);
    private final ModelResolutionQuery modelResolution = mock(ModelResolutionQuery.class);
    private final List<AgentExecution> saved = new ArrayList<>();
    private final AgentExecutionRepository repository = saved::add;

    private final QueueCompletion completion = new QueueCompletion();
    private final AskPolicyAgent agent = new AskPolicyAgent(knowledge, completion, modelResolution,
        repository, new ObjectMapper());

    @BeforeEach
    void setUp() {
        when(modelResolution.resolve(tenantId, userId, null)).thenReturn(Optional.of(
            new ResolvedModelInfo(echoModel, "gpt-4o", UUID.randomUUID(), "OpenAI")));
    }

    @Test
    void semTrechosNaoChamaOLlm() {
        when(knowledge.search(tenantId, userId, "pergunta")).thenReturn(List.of());

        PolicyAnswer answer = agent.ask(command());

        assertThat(answer.answer()).isEqualTo(AskPolicyAgent.NOT_FOUND);
        assertThat(answer.requestId()).isNull();
        assertThat(answer.completion()).isNull();
        assertThat(completion.commands).isEmpty();
        assertThat(saved.get(saved.size() - 1).status())
            .isEqualTo(AgentExecutionStatus.COMPLETED);
        assertThat(saved.get(saved.size() - 1).requestId()).isNull();
        assertThat(saved.get(saved.size() - 1).documentIds()).isEmpty();
    }

    @Test
    void sucessoRegistraRequestIdEDocumentos() {
        KnowledgeHit hit = hit("Política", "texto");
        when(knowledge.search(tenantId, userId, "pergunta")).thenReturn(List.of(hit));
        CompletionResult expected = result("Resposta [1]", List.of());
        completion.results.add(expected);

        PolicyAnswer answer = agent.ask(command());

        assertThat(answer.answer()).isEqualTo("Resposta [1]");
        assertThat(answer.requestId()).isEqualTo(expected.requestId());
        assertThat(answer.sources()).containsExactly(hit);
        assertThat(answer.toolsUsed()).isEmpty();
        assertThat(completion.commands.get(0).purpose()).isEqualTo("POLICY_AGENT");
        assertThat(completion.commands.get(0).tools()).hasSize(2);
        AgentExecution execution = saved.get(saved.size() - 1);
        assertThat(execution.status()).isEqualTo(AgentExecutionStatus.COMPLETED);
        assertThat(execution.documentIds()).containsExactly(hit.documentId());
        assertThat(execution.toolsCalled()).isEmpty();
    }

    @Test
    void executaBuscarPoliticasEDevolveResultadoAoLlm() {
        KnowledgeHit hit = hit("Política", "texto");
        when(knowledge.search(tenantId, userId, "pergunta")).thenReturn(List.of(hit));
        when(knowledge.search(tenantId, userId, "viagens")).thenReturn(List.of(hit));
        completion.results.add(result(null, List.of(
            new ToolCall("call-1", AgentPromptBuilder.TOOL_SEARCH, "{\"consulta\":\"viagens\"}"))));
        completion.results.add(result("Resposta final", List.of()));

        PolicyAnswer answer = agent.ask(command());

        assertThat(answer.answer()).isEqualTo("Resposta final");
        assertThat(answer.toolsUsed()).containsExactly(AgentPromptBuilder.TOOL_SEARCH);
        assertThat(completion.commands).hasSize(2);
        assertThat(completion.commands.get(1).messages())
            .anySatisfy(message -> {
                assertThat(message.role()).isEqualTo("TOOL");
                assertThat(message.toolCallId()).isEqualTo("call-1");
                assertThat(message.content()).contains("texto");
            });
        assertThat(saved.get(saved.size() - 1).toolsCalled())
            .containsExactly(AgentPromptBuilder.TOOL_SEARCH);
    }

    @Test
    void ferramentaDesconhecidaEhIgnoradaERegistrada() {
        when(knowledge.search(tenantId, userId, "pergunta")).thenReturn(List.of(hit("P", "t")));
        completion.results.add(result(null, List.of(new ToolCall("x", "apagar_tudo", "{}"))));
        completion.results.add(result("ok", List.of()));

        PolicyAnswer answer = agent.ask(command());

        assertThat(answer.toolsUsed()).isEmpty();
        assertThat(saved.get(saved.size() - 1).toolsCalled()).containsExactly("apagar_tudo");
        assertThat(completion.commands.get(1).messages())
            .anySatisfy(message -> assertThat(message.content())
                .contains("ferramenta desconhecida"));
    }

    @Test
    void limitaDuasRodadasDeFerramentas() {
        when(knowledge.search(tenantId, userId, "pergunta")).thenReturn(List.of(hit("P", "t")));
        for (int i = 0; i < 3; i++) {
            completion.results.add(result(null, List.of(
                new ToolCall("c" + i, AgentPromptBuilder.TOOL_MODEL, "{}"))));
        }

        PolicyAnswer answer = agent.ask(command());

        assertThat(completion.commands).hasSize(3);
        assertThat(answer.answer()).isEqualTo(AskPolicyAgent.NOT_FOUND);
    }

    @Test
    void falhaDaCompletionMarcaExecucaoFailed() {
        when(knowledge.search(tenantId, userId, "pergunta")).thenReturn(List.of(hit("P", "t")));
        completion.failure = new AIProviderFailureException("HTTP_500");

        assertThatThrownBy(() -> agent.ask(command()))
            .isInstanceOf(AIProviderFailureException.class);

        assertThat(saved.get(saved.size() - 1).status()).isEqualTo(AgentExecutionStatus.FAILED);
    }

    private AskPolicyCommand command() {
        return new AskPolicyCommand(tenantId, userId, "pergunta", null);
    }

    private static KnowledgeHit hit(String title, String text) {
        return new KnowledgeHit(UUID.randomUUID(), text, UUID.randomUUID(), title, 1, 1.0);
    }

    private static CompletionResult result(String content, List<ToolCall> tools) {
        return new CompletionResult(UUID.randomUUID(), content, tools, UUID.randomUUID(), "gpt-4o",
            UUID.randomUUID(), "OpenAI", 10L, 20L, new BigDecimal("0.000700"), "KNOWN", 42L);
    }

    private static final class QueueCompletion implements AICompletionService {
        private final List<CompletionResult> results = new ArrayList<>();
        private final List<CompletionCommand> commands = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public CompletionResult complete(CompletionCommand command) {
            commands.add(command);
            if (failure != null) {
                throw failure;
            }
            return results.remove(0);
        }
    }
}
