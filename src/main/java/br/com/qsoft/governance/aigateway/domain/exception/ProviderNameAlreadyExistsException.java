package br.com.qsoft.governance.aigateway.domain.exception;

/** Já existe um Provider com o mesmo nome no tenant. */
public class ProviderNameAlreadyExistsException extends RuntimeException {

    public ProviderNameAlreadyExistsException(String name) {
        super("Já existe um provider com o nome '" + name + "' neste tenant.");
    }
}
