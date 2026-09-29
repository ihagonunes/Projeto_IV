package br.com.qsoft.governance.aigateway.domain.exception;

/**
 * O {@code requestedModelId} não pertence ao {@code allowedModelIds} do
 * departamento (SPI-83). Sem fallback/heurística.
 */
public class ModelNotAllowedException extends RuntimeException {

    public ModelNotAllowedException() {
        super("O modelo solicitado não é permitido para o seu departamento. Contate o administrador.");
    }
}
