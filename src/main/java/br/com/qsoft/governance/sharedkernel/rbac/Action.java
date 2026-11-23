package br.com.qsoft.governance.sharedkernel.rbac;

/**
 * Ação executável sobre um recurso.
 * MANAGE representa administração ampla do recurso (create/read/update/delete).
 * <p>
 * Vocabulário <b>transversal</b> de autorização (ADR-34) — o modelo permanece
 * no IAM (ADR-04 / ADR-33).
 */
public enum Action {
    CREATE,
    READ,
    UPDATE,
    DELETE,
    MANAGE,
    RUN
}
