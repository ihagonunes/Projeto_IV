package br.com.qsoft.governance.aigateway.api.exception;

/** O provider excedeu o timeout configurado (HTTP 504). */
public class AIProviderTimeoutException extends RuntimeException {

    public AIProviderTimeoutException() {
        super("O provider de IA excedeu o tempo limite.");
    }
}
