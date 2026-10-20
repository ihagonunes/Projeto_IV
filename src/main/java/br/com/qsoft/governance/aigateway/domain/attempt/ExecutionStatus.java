package br.com.qsoft.governance.aigateway.domain.attempt;

/** Estado de execução da tentativa (V017). */
public enum ExecutionStatus {
    NOT_STARTED,
    IN_FLIGHT,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    OUTCOME_UNKNOWN
}
