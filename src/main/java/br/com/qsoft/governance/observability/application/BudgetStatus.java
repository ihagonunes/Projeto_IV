package br.com.qsoft.governance.observability.application;

/** Status do orçamento calculado na leitura (SPEC-02 §3); não é persistido. */
public enum BudgetStatus {
    OK,
    ALERTA,
    EXCEDIDO,
    SEM_ORCAMENTO
}
