package br.com.qsoft.governance.iam.domain.exception;

/**
 * Já existe uma concessão ativa idêntica (mesmo usuário, papel e departamento).
 * Deve responder 409.
 */
public class RoleAssignmentAlreadyExistsException extends RuntimeException {

    public RoleAssignmentAlreadyExistsException() {
        super("Já existe uma concessão ativa idêntica.");
    }
}
