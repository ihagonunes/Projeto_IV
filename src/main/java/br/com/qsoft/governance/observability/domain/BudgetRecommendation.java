package br.com.qsoft.governance.observability.domain;

/** Recomendação determinística da projeção de orçamento (ADR-41). */
public enum BudgetRecommendation {
    DENTRO_DO_ORCAMENTO,
    ATENCAO,
    ESGOTA_NO_MES,
    JA_EXCEDIDO,
    SEM_ORCAMENTO
}
