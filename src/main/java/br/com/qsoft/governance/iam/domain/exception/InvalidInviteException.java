package br.com.qsoft.governance.iam.domain.exception;

/**
 * Convite inválido, expirado ou já utilizado. Mensagem genérica para não
 * revelar detalhes (evita enumeração).
 */
public class InvalidInviteException extends RuntimeException {

    public InvalidInviteException() {
        super("Convite inválido, expirado ou já utilizado.");
    }
}
