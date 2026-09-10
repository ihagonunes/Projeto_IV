package br.com.qsoft.governance.iam.api.model;

/**
 * Resultado de uma checagem de permissão (Determinística, no backend).
 */
public record AuthorizationResult(boolean allowed, String denialReason) {

    public static AuthorizationResult allow() {
        return new AuthorizationResult(true, null);
    }

    public static AuthorizationResult deny(String reason) {
        return new AuthorizationResult(false, reason);
    }
}
