package br.com.qsoft.governance.aigateway.api.exception;

/** O provider de IA falhou (HTTP 502). O código técnico é curto e sem conteúdo. */
public class AIProviderFailureException extends RuntimeException {

    private final String errorCode;

    public AIProviderFailureException(String errorCode) {
        super("O provider de IA falhou.");
        this.errorCode = errorCode == null ? "PROVIDER_FAILURE" : errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
