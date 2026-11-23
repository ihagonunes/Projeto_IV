package br.com.qsoft.governance.agents.application;

/** Estado do fluxo não permite a operação (ex.: já publicado). Na API vira 409. */
public class AgentFlowStateConflictException extends RuntimeException {

    public AgentFlowStateConflictException() {
        super("O estado atual do fluxo não permite esta operação.");
    }
}
