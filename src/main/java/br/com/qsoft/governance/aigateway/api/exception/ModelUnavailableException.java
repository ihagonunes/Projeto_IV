package br.com.qsoft.governance.aigateway.api.exception;

/** O modelo está DISABLED no catálogo (HTTP 409). */
public class ModelUnavailableException extends RuntimeException {

    public ModelUnavailableException() {
        super("Modelo indisponível. Contate o administrador.");
    }
}
