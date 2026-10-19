package br.com.qsoft.governance.iam.domain.exception;

/**
 * Concessão inexistente, de outro tenant, de outro usuário ou já inativa.
 * Deve responder 404 (não revelar existência cross-tenant/tenant).
 */
public class RoleAssignmentNotFoundException extends RuntimeException {

    public RoleAssignmentNotFoundException() {
        super("Concessão não encontrada.");
    }
}
