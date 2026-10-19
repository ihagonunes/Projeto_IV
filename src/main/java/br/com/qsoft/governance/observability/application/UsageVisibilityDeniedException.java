package br.com.qsoft.governance.observability.application;

/**
 * Sem visibilidade de uso para o ator (escopo {@code None} devolvido pelo IAM).
 * Deve responder 403. Exceção local do módulo para não importar internals do IAM
 * (a allowlist só permite {@code iam.api.*}).
 */
public class UsageVisibilityDeniedException extends RuntimeException {

    public UsageVisibilityDeniedException() {
        super("Sem visibilidade de uso.");
    }
}
