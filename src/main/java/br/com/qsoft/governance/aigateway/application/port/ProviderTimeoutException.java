package br.com.qsoft.governance.aigateway.application.port;

/** O provider excedeu o timeout configurado. */
public class ProviderTimeoutException extends RuntimeException {

    public ProviderTimeoutException() {
        super("Provider timeout.");
    }
}
