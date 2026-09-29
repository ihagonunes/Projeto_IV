package br.com.qsoft.governance.aigateway.domain.model;

/**
 * Estado de um Model no catálogo. Apenas AVAILABLE pode ser roteado (SPI-83).
 */
public enum ModelStatus {
    AVAILABLE,
    DISABLED
}
