package br.com.qsoft.governance.iam.domain.rbac;

/**
 * Origem de uma concessão de papel (ADR-37): registra como a concessão nasceu.
 */
public enum GrantOrigin {
    BOOTSTRAP,
    INVITE,
    DIRECT,
    MIGRATION
}
