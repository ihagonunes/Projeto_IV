package br.com.qsoft.governance.iam.api.model;

/**
 * Permissões administrativas de plataforma (conjunto que caracteriza ADMIN).
 * Mapeadas a partir das operações admin-only da baseline (§22 / ADR-04).
 */
public enum Permission {

    MANAGE_TENANT,
    MANAGE_USERS,
    MANAGE_HIERARCHY,
    MANAGE_DEPARTMENTS,
    MANAGE_MODEL_POLICIES,
    MANAGE_AI_CATALOG,
    MANAGE_KNOWLEDGE,
    MANAGE_LICENSES,
    MANAGE_TRAINING
}
