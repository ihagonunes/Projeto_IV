package br.com.qsoft.governance.aigateway.domain.exception;

import java.util.UUID;

/** Model não encontrado no tenant do contexto (404 — não revela cross-tenant). */
public class ModelNotFoundException extends RuntimeException {

    public ModelNotFoundException(UUID modelId) {
        super("Modelo não encontrado: " + modelId + ".");
    }
}
