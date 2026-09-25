package br.com.qsoft.governance.aigateway.domain.exception;

/**
 * O departamento do usuário não tem ModelPolicy configurada (nem default) —
 * SPI-85. O sistema <b>não</b> escolhe um modelo alternativo: orienta contatar o
 * administrador.
 */
public class NoModelConfiguredException extends RuntimeException {

    public NoModelConfiguredException() {
        super("Nenhum modelo de IA está configurado para o seu departamento. Contate o administrador.");
    }
}
