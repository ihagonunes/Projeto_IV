package br.com.qsoft.governance.aigateway.api.completion;

import java.util.Objects;

/** Chamada de ferramenta pedida pelo modelo. */
public record ToolCall(String id, String name, String argumentsJson) {

    public ToolCall {
        Objects.requireNonNull(id, "id é obrigatório.");
        Objects.requireNonNull(name, "name é obrigatório.");
        argumentsJson = argumentsJson == null ? "{}" : argumentsJson;
    }
}
