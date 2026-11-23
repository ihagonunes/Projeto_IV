package br.com.qsoft.governance.agents.domain;

import java.util.Objects;
import java.util.UUID;

/** Resultado de uma completion de nó `agent` (sem prompt/resposta de erro). */
public record AgentCompletion(UUID requestId, String content) {

    public AgentCompletion {
        Objects.requireNonNull(requestId, "requestId é obrigatório.");
        content = content == null ? "" : content;
    }
}
