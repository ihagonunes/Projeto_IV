package br.com.qsoft.governance.agents.domain;

/** Status de uma execução de pipeline (ADR-45). */
public enum ExecutionStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    CANCELLED
}
