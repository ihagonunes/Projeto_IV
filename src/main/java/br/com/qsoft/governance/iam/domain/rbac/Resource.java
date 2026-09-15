package br.com.qsoft.governance.iam.domain.rbac;

/**
 * Recurso protegido do sistema sobre o qual uma ação pode ser executada.
 * Faz parte do modelo de permissões do IAM (SPI-39).
 */
public enum Resource {
    TENANT,
    USER,
    ROLE,
    PERMISSION,
    INVITE,
    DEPARTMENT,
    PROJECT,
    MODEL,
    AI_TOOL,
    CHAT,
    KNOWLEDGE_DOCUMENT,
    LICENSE,
    TRAINING,
    USAGE,
    GOVERNANCE_SCORE
}
