package br.com.qsoft.governance.iam.domain.exception;

/**
 * Convite inexistente ou pertencente a outro tenant. A mensagem é genérica para
 * não revelar a existência do recurso em outro tenant (ADR-36, item 10: 404).
 */
public class InviteNotFoundException extends RuntimeException {

    public InviteNotFoundException() {
        super("Convite não encontrado.");
    }
}
