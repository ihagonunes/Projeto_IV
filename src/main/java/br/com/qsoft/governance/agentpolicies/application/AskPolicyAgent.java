package br.com.qsoft.governance.agentpolicies.application;

import br.com.qsoft.governance.aigateway.api.completion.AICompletionService;
import br.com.qsoft.governance.aigateway.api.completion.CompletionCommand;
import br.com.qsoft.governance.aigateway.api.completion.CompletionMessage;
import br.com.qsoft.governance.aigateway.api.completion.CompletionResult;
import br.com.qsoft.governance.aigateway.api.completion.ToolCall;
import br.com.qsoft.governance.aigateway.api.query.ModelResolutionQuery;
import br.com.qsoft.governance.agentpolicies.application.port.AgentExecutionRepository;
import br.com.qsoft.governance.agentpolicies.domain.AgentExecution;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Orquestração do agente de políticas (SPEC-05 §3): recupera trechos, chama a
 * completion com prompt fixo e ferramentas da allowlist (até 2 rodadas) e
 * registra apenas metadados de auditoria. Sem trechos, o LLM não é chamado.
 */
@Service
public class AskPolicyAgent {

    public static final String NOT_FOUND =
        "Não encontrei política aplicável para responder a essa pergunta.";
    static final int MAX_ROUNDS = 2;
    private static final int TOOL_NAME_MAX = 60;
    private static final Set<String> ALLOWED_TOOLS = Set.of(
        AgentPromptBuilder.TOOL_SEARCH, AgentPromptBuilder.TOOL_MODEL);
    private static final Logger log = LoggerFactory.getLogger(AskPolicyAgent.class);

    private final KnowledgeAccessService knowledgeAccess;
    private final AICompletionService completionService;
    private final ModelResolutionQuery modelResolution;
    private final AgentExecutionRepository executionRepository;
    private final ObjectMapper objectMapper;

    public AskPolicyAgent(KnowledgeAccessService knowledgeAccess,
                          AICompletionService completionService,
                          ModelResolutionQuery modelResolution,
                          AgentExecutionRepository executionRepository,
                          ObjectMapper objectMapper) {
        this.knowledgeAccess = knowledgeAccess;
        this.completionService = completionService;
        this.modelResolution = modelResolution;
        this.executionRepository = executionRepository;
        this.objectMapper = objectMapper;
    }

    public PolicyAnswer ask(AskPolicyCommand command) {
        TenantId tenantId = command.tenantId();
        UserId userId = command.userId();
        AgentExecution execution = AgentExecution.start(UUID.randomUUID(), tenantId, userId);
        executionRepository.save(execution);

        List<KnowledgeHit> hits = knowledgeAccess.search(tenantId, userId, command.question());
        if (hits.isEmpty()) {
            execution.complete(null, List.of(), List.of());
            executionRepository.save(execution);
            return new PolicyAnswer(NOT_FOUND, List.of(), List.of(), null, null);
        }

        Set<UUID> documentIds = new LinkedHashSet<>();
        hits.forEach(hit -> documentIds.add(hit.documentId()));
        Set<String> executed = new LinkedHashSet<>();
        Set<String> recorded = new LinkedHashSet<>();
        List<CompletionMessage> messages =
            new ArrayList<>(AgentPromptBuilder.build(hits, command.question()));

        try {
            CompletionResult result = complete(tenantId, userId, command.requestedModelId(),
                messages);
            int rounds = 0;
            while (!result.toolCalls().isEmpty() && rounds < MAX_ROUNDS) {
                rounds++;
                for (ToolCall toolCall : result.toolCalls()) {
                    messages.add(new CompletionMessage("TOOL",
                        executeTool(toolCall, tenantId, userId, command.question(), executed,
                            recorded, documentIds),
                        toolCall.id()));
                }
                result = complete(tenantId, userId, command.requestedModelId(), messages);
            }

            execution.complete(result.requestId(), documentIds, recorded);
            executionRepository.save(execution);
            return new PolicyAnswer(
                result.content() == null ? NOT_FOUND : result.content(),
                hits, List.copyOf(executed), result.requestId(), result);
        } catch (RuntimeException ex) {
            execution.fail(recorded);
            executionRepository.save(execution);
            throw ex;
        }
    }

    private CompletionResult complete(TenantId tenantId, UserId userId, UUID requestedModelId,
                                      List<CompletionMessage> messages) {
        return completionService.complete(new CompletionCommand(tenantId, userId,
            requestedModelId, "POLICY_AGENT", messages, AgentPromptBuilder.tools()));
    }

    private String executeTool(ToolCall toolCall, TenantId tenantId, UserId userId,
                               String question, Set<String> executed, Set<String> recorded,
                               Set<UUID> documentIds) {
        String name = toolCall.name();
        if (!ALLOWED_TOOLS.contains(name)) {
            String recordedName = truncate(name);
            recorded.add(recordedName);
            log.warn("Ferramenta desconhecida ignorada: {}", recordedName);
            return "{\"erro\":\"ferramenta desconhecida\"}";
        }
        executed.add(name);
        recorded.add(name);

        if (AgentPromptBuilder.TOOL_SEARCH.equals(name)) {
            String query = queryFrom(toolCall.argumentsJson(), question);
            List<KnowledgeHit> hits = knowledgeAccess.search(tenantId, userId, query);
            hits.forEach(hit -> documentIds.add(hit.documentId()));
            return format(hits);
        }

        return modelResolution.resolve(tenantId, userId, null)
            .map(model -> "{\"modelo\":\"" + model.modelName() + "\",\"provider\":\""
                + model.providerName() + "\"}")
            .orElse("{\"modelo\":null,\"motivo\":\"nenhum modelo permitido\"}");
    }

    private String queryFrom(String argumentsJson, String fallback) {
        try {
            String query = objectMapper.readTree(argumentsJson).path("consulta").asText("");
            return query.isBlank() ? fallback : query;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static String format(List<KnowledgeHit> hits) {
        if (hits.isEmpty()) {
            return "Nenhum trecho encontrado.";
        }
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < hits.size(); index++) {
            KnowledgeHit hit = hits.get(index);
            text.append('[').append(index + 1).append("] (")
                .append(hit.title()).append(", v").append(hit.versionNumber()).append(")\n")
                .append(hit.text()).append('\n');
        }
        return text.toString();
    }

    private static String truncate(String value) {
        if (value == null) {
            return "unknown";
        }
        return value.length() <= TOOL_NAME_MAX ? value : value.substring(0, TOOL_NAME_MAX);
    }
}
