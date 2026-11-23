package br.com.qsoft.governance.agents.infrastructure.web;

import br.com.qsoft.governance.agents.domain.FlowValidationError;

/** Erro estável de validação com caminho no JSON. */
public record FlowValidationErrorResponse(String code, String message, String path) {

    static FlowValidationErrorResponse from(FlowValidationError error) {
        return new FlowValidationErrorResponse(error.code(), error.message(), error.path());
    }
}
