package br.com.qsoft.governance.agents.application;

/** Fluxo inexistente ou fora do escopo do ator. Na API vira 404. */
public class AgentFlowNotFoundException extends RuntimeException {

    public AgentFlowNotFoundException() {
        super("Fluxo de agentes não encontrado.");
    }
}
