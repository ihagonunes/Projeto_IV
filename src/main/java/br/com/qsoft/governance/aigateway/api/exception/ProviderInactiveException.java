package br.com.qsoft.governance.aigateway.api.exception;

/** O provider do modelo está INACTIVE (HTTP 409). */
public class ProviderInactiveException extends RuntimeException {

    public ProviderInactiveException() {
        super("Provider inativo. Contate o administrador.");
    }
}
