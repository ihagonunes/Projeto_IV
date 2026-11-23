package br.com.qsoft.governance.agents.domain;

import java.util.List;

/** Resultado da validação server-side do grafo (ADR-44 §8). */
public record FlowValidation(boolean valid, boolean needsMerge,
                             List<FlowValidationError> errors) {

    public FlowValidation {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
