package br.com.qsoft.governance.agents.domain;

/** Erro de validação com código estável e caminho no JSON (sem stack trace). */
public record FlowValidationError(String code, String message, String path) {

    public FlowValidationError {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code é obrigatório.");
        }
        message = message == null ? "" : message;
        path = path == null ? "" : path;
    }
}
