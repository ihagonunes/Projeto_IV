package br.com.qsoft.governance.aigateway.domain.exception;

/**
 * O Model selecionado está DISABLED (SPI-83/85). Sem fallback: o routing é
 * determinístico.
 */
public class ModelUnavailableException extends RuntimeException {

    public ModelUnavailableException() {
        super("O modelo de IA selecionado está indisponível. Contate o administrador.");
    }
}
