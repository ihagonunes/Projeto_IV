package br.com.qsoft.governance.iam.api.rbac;

/**
 * Modo de avaliação do gate declarado em {@link RequiresPermission} (ADR-36).
 * <p>
 * {@link #TENANT_ONLY} (padrão) exige uma concessão de escopo TENANT — é o
 * modo fechado por padrão. {@link #BY_TARGET} aceita qualquer concessão ativa
 * que contenha a permissão e obriga o caso de uso a aplicar a checagem fina
 * ({@code isAllowed}/{@code visibilityFor}) sobre o alvo.
 */
public enum Targeting {
    TENANT_ONLY,
    BY_TARGET
}
