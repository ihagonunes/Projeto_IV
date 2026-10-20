package br.com.qsoft.governance.aigateway.api.exception;

/** O modelo pedido não pertence à política do departamento (HTTP 403). */
public class ModelNotAllowedException extends RuntimeException {

    public ModelNotAllowedException() {
        super("Modelo não permitido pela política do departamento.");
    }
}
