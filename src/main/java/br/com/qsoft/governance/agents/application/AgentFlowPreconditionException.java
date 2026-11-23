package br.com.qsoft.governance.agents.application;

/** Conflito otimista no fluxo (If-Match/row_version). Na API vira 412. */
public class AgentFlowPreconditionException extends RuntimeException {

    public AgentFlowPreconditionException() {
        super("O fluxo foi alterado por outra requisição. Recarregue e tente novamente.");
    }
}
