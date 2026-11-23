package br.com.qsoft.governance.agents.domain;

/** Falha transitória/timeout da completion, já sem conteúdo sensível. */
public class AgentFlowTimeoutException extends RuntimeException {

    public AgentFlowTimeoutException() {
        super("A etapa excedeu o tempo limite.");
    }
}
