package br.com.qsoft.governance.aigateway.domain.attempt;

/** Estado de entrega do evento de uso (V017/ADR-38). */
public enum DeliveryStatus {
    NOT_APPLICABLE,
    PENDING,
    DELIVERED,
    DEAD
}
