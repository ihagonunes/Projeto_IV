package br.com.qsoft.governance.sharedkernel.rbac;

/**
 * Escopo fixo de uma concessão de papel (ADR-36).
 * <p>
 * O escopo pertence à concessão e é derivado do papel de sistema; nunca do
 * rótulo organizacional nem do departamento atual do usuário.
 */
public enum ScopeType {
    SELF,
    DEPARTMENT,
    TENANT
}
