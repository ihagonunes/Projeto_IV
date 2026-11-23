package br.com.qsoft.governance.agents.application;

/** Versão corrente ausente ou inválida. Na API vira 422. */
public class AgentFlowNotValidatedException extends RuntimeException {

    public AgentFlowNotValidatedException() {
        super("A versão corrente do fluxo não está válida para publicação.");
    }
}
