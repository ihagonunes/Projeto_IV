package br.com.qsoft.governance.agentpolicies.application;

import br.com.qsoft.governance.aigateway.api.completion.CompletionResult;
import java.util.List;
import java.util.UUID;

/** Resultado de uma pergunta ao agente: resposta, fontes, ferramentas e recibo. */
public record PolicyAnswer(
    String answer,
    List<KnowledgeHit> sources,
    List<String> toolsUsed,
    UUID requestId,
    CompletionResult completion
) {
    public PolicyAnswer {
        sources = sources == null ? List.of() : List.copyOf(sources);
        toolsUsed = toolsUsed == null ? List.of() : List.copyOf(toolsUsed);
    }
}
