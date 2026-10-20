package br.com.qsoft.governance.aigateway.api.exception;

/**
 * A intenção da tentativa não pôde ser registrada antes da chamada ao provider
 * (HTTP 503). A chamada NÃO é feita.
 */
public class AIAttemptNotRecordedException extends RuntimeException {

    public AIAttemptNotRecordedException() {
        super("Não foi possível registrar a tentativa de IA.");
    }
}
