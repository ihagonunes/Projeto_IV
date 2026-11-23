package br.com.qsoft.governance.agents.domain;

/** Status de um nó dentro da execução (ADR-45). */
public enum ExecutionStepStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    SKIPPED
}
