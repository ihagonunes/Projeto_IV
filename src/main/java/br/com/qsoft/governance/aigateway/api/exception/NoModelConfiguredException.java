package br.com.qsoft.governance.aigateway.api.exception;

/** Não há política/modelo configurado para o departamento do usuário (HTTP 409). */
public class NoModelConfiguredException extends RuntimeException {

    public NoModelConfiguredException() {
        super("Nenhum modelo permitido configurado para o departamento. Contate o administrador.");
    }
}
