package br.com.qsoft.governance.aigateway.domain.exception;

/**
 * O Provider do modelo selecionado está INACTIVE (SPI-83/85). O routing é
 * determinístico: não há fallback para outro modelo.
 */
public class ProviderInactiveException extends RuntimeException {

    public ProviderInactiveException() {
        super("O provider do modelo selecionado está inativo. Contate o administrador.");
    }
}
