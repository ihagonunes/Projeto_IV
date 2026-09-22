package br.com.qsoft.governance.iam.domain.rbac;

/**
 * Ação executável sobre um recurso.
 * MANAGE representa administração ampla do recurso (create/read/update/delete).
 */
public enum Action {
    CREATE,
    READ,
    UPDATE,
    DELETE,
    MANAGE
}
