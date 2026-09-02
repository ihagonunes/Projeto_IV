package br.com.qsoft.governance.iam.domain.exception;

import br.com.qsoft.governance.sharedkernel.id.UserId;

/**
 * Lançada quando um usuário não é encontrado.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UserId userId) {
        super("Usuário não encontrado: " + userId + ".");
    }
}
