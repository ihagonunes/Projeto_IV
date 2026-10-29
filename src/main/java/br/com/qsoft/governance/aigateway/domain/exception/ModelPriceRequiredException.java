package br.com.qsoft.governance.aigateway.domain.exception;

/**
 * Um modelo só pode ficar AVAILABLE (ou ter custo calculado) com os dois
 * preços definidos. Zero explícito é permitido; nulo não (S4-D12/ADR-38).
 */
public class ModelPriceRequiredException extends RuntimeException {

    public ModelPriceRequiredException() {
        super("Preço de entrada e de saída são obrigatórios para disponibilizar o modelo.");
    }
}
