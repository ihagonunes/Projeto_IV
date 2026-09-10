package br.com.qsoft.governance.iam.domain.exception;

/**
 * Lançada quando se tenta criar um usuário com e-mail já existente no mesmo tenant.
 */
public class UserEmailAlreadyExistsException extends RuntimeException {

    public UserEmailAlreadyExistsException(String email) {
        super("Já existe um usuário com o e-mail '" + email + "' neste tenant.");
    }
}
