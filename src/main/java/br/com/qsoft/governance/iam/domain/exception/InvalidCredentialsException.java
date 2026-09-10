package br.com.qsoft.governance.iam.domain.exception;

/**
 * Credenciais inválidas. Mensagem genérica para não revelar qual campo falhou
 * (evita enumeração de tenant/usuário, ADR-30).
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Credenciais inválidas.");
    }
}
