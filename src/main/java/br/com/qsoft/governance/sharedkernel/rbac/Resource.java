package br.com.qsoft.governance.sharedkernel.rbac;

/**
 * Recurso protegido do sistema sobre o qual uma ação pode ser executada.
 * <p>
 * Vocabulário <b>transversal</b> de autorização (ADR-34): cada módulo declara
 * quais recursos protege. O <b>modelo</b> de autorização (papéis por tenant,
 * avaliação de política, escopo por department) permanece no IAM
 * (ADR-04 / ADR-33) — aqui vive apenas o vocabulário.
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
