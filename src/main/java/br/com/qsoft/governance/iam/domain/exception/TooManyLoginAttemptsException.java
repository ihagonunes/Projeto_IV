package br.com.qsoft.governance.iam.domain.exception;

/**
 * Lançada quando o rate limiting do login (SPI-67) bloqueia temporariamente
 * novas tentativas. A mensagem é genérica para não revelar se a conta existe;
 * o tempo restante é exposto para alimentar o cabeçalho HTTP {@code Retry-After}.
 */
public class TooManyLoginAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException(long retryAfterSeconds) {
        super("Muitas tentativas de login. Tente novamente mais tarde.");
        this.retryAfterSeconds = Math.max(retryAfterSeconds, 0);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
