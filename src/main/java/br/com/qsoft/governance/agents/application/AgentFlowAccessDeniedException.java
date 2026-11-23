package br.com.qsoft.governance.agents.application;

/** Operação pedida fora do escopo de autorização do ator. Na API vira 403. */
public class AgentFlowAccessDeniedException extends RuntimeException {

    public AgentFlowAccessDeniedException() {
        super("Acesso negado ao fluxo de agentes.");
    }
}
