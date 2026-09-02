package br.com.qsoft.governance.iam.domain.user;

/**
 * Níveis hierárquicos padronizados da organização.
 * ADMIN NÃO é um nível hierárquico; é um conjunto de permissões (ADR-04).
 */
public enum HierarchyLevel {
    COLLABORATOR,
    LEADER,
    MANAGER,
    EXECUTIVE
}
