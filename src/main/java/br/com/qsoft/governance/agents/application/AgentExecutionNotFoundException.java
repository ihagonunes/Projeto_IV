package br.com.qsoft.governance.agents.application;

/** Execução inexistente ou fora do escopo do ator. Na API vira 404. */
public class AgentExecutionNotFoundException extends RuntimeException {

    public AgentExecutionNotFoundException() {
        super("Execução de agentes não encontrada.");
    }
}
