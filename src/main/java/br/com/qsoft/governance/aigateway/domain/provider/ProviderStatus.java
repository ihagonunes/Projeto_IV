package br.com.qsoft.governance.aigateway.domain.provider;

/**
 * Estado de um Provider do catálogo. Vários Providers podem estar ACTIVE
 * simultaneamente (ADR-09).
 */
public enum ProviderStatus {
    ACTIVE,
    INACTIVE
}
