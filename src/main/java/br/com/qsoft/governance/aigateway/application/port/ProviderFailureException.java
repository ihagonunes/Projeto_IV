package br.com.qsoft.governance.aigateway.application.port;

/**
 * Falha técnica do provider. {@code errorCode} é um código curto (ex.:
 * {@code HTTP_429}, {@code TIMEOUT}, {@code PARSE}) — nunca conteúdo.
 */
public class ProviderFailureException extends RuntimeException {

    private final String errorCode;
    private final boolean billingDeclaredZero;

    public ProviderFailureException(String errorCode) {
        this(errorCode, false);
    }

    public ProviderFailureException(String errorCode, boolean billingDeclaredZero) {
        super("Provider failure: " + errorCode);
        this.errorCode = errorCode == null ? "PROVIDER_FAILURE" : errorCode;
        this.billingDeclaredZero = billingDeclaredZero;
    }

    public String errorCode() {
        return errorCode;
    }

    /** O adaptador sabe que o provider não cobrou a chamada (custo KNOWN = 0). */
    public boolean billingDeclaredZero() {
        return billingDeclaredZero;
    }
}
