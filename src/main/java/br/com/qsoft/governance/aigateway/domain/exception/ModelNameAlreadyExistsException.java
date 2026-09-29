package br.com.qsoft.governance.aigateway.domain.exception;

/** Já existe um Model com o mesmo nome no tenant. */
public class ModelNameAlreadyExistsException extends RuntimeException {

    public ModelNameAlreadyExistsException(String name) {
        super("Já existe um modelo com o nome '" + name + "' neste tenant.");
    }
}
