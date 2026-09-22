package br.com.qsoft.governance.iam.domain.exception;

/**
 * Lançada quando o rate limiting do login (SPI-67) bloqueia temporariamente
 * novas tentativas. A mensagem é genérica para não revelar se a conta existe.
 */
public class TooManyLoginAttemptsException extends RuntimeException {

    public TooManyLoginAttemptsException() {
        super("Muitas tentativas de login. Tente novamente mais tarde.");
    }
}
