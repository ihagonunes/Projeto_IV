package br.com.qsoft.governance.aigateway.api.exception;

/** O ator não tem visibilidade sobre o filtro/tentativa pedidos (HTTP 403). */
public class AIAttemptAccessDeniedException extends RuntimeException {

    public AIAttemptAccessDeniedException() {
        super("Acesso negado às tentativas de IA.");
    }
}
