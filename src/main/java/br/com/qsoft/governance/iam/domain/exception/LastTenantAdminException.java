package br.com.qsoft.governance.iam.domain.exception;

/**
 * Tentativa de revogar ou desativar o último {@code TENANT_ADMIN} ativo do
 * tenant (ADR-37 item 5). Deve responder 409 para evitar lockout irreversível.
 */
public class LastTenantAdminException extends RuntimeException {

    public LastTenantAdminException() {
        super("Não é possível remover o último administrador do tenant.");
    }
}
