package br.com.qsoft.governance.iam.domain.exception;

/**
 * Lançada quando um usuário tenta realizar uma operação para a qual
 * não está autorizado (ex.: alterar nível de usuário de outro tenant).
 */
public class AuthorizationDeniedException extends RuntimeException {

    public AuthorizationDeniedException(String message) {
        super(message);
    }
}
