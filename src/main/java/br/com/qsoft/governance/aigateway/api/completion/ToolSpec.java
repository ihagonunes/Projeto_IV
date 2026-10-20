package br.com.qsoft.governance.aigateway.api.completion;

import java.util.Objects;

/** Especificação de uma ferramenta oferecida ao modelo (formato JSON Schema). */
public record ToolSpec(String name, String description, String jsonSchemaParameters) {

    public ToolSpec {
        Objects.requireNonNull(name, "name é obrigatório.");
        Objects.requireNonNull(jsonSchemaParameters, "jsonSchemaParameters é obrigatório.");
    }
}
