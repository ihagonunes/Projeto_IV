package br.com.qsoft.governance.agentpolicies.application;

import br.com.qsoft.governance.aigateway.api.completion.CompletionMessage;
import br.com.qsoft.governance.aigateway.api.completion.ToolSpec;
import java.util.ArrayList;
import java.util.List;

/**
 * Prompt fixo do agente de políticas (SPEC-05 §3.4). O texto do sistema vive no
 * código e não é configurável; os trechos entram numerados na ordem do ranking.
 */
public final class AgentPromptBuilder {

    public static final String SYSTEM_PROMPT = """
        Você é o agente de políticas da empresa. Responda somente com base nos trechos numerados fornecidos.
        Cite as fontes no formato [n], usando o número do trecho. Se os trechos não contiverem a resposta,
        diga claramente que não encontrou política aplicável. Não invente regras, prazos ou exceções.
        Responda em português do Brasil, de forma objetiva.
        """;

    public static final String TOOL_SEARCH = "buscar_politicas";
    public static final String TOOL_MODEL = "modelo_permitido";

    private AgentPromptBuilder() {
    }

    public static List<CompletionMessage> build(List<KnowledgeHit> hits, String question) {
        StringBuilder context = new StringBuilder("Trechos:\n");
        for (int index = 0; index < hits.size(); index++) {
            KnowledgeHit hit = hits.get(index);
            context.append('[').append(index + 1).append("] (")
                .append(hit.title()).append(", v").append(hit.versionNumber()).append(")\n")
                .append(hit.text()).append("\n\n");
        }
        context.append("Pergunta: ").append(question);
        return List.of(
            new CompletionMessage("SYSTEM", SYSTEM_PROMPT, null),
            new CompletionMessage("USER", context.toString(), null)
        );
    }

    public static List<ToolSpec> tools() {
        List<ToolSpec> tools = new ArrayList<>();
        tools.add(new ToolSpec(TOOL_SEARCH,
            "Busca trechos de políticas aplicáveis ao usuário",
            "{\"type\":\"object\",\"properties\":{\"consulta\":{\"type\":\"string\"}},"
                + "\"required\":[\"consulta\"]}"));
        tools.add(new ToolSpec(TOOL_MODEL,
            "Modelo de IA permitido para o usuário",
            "{\"type\":\"object\",\"properties\":{}}"));
        return List.copyOf(tools);
    }
}
