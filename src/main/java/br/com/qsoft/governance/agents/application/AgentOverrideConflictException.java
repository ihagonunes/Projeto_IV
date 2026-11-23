package br.com.qsoft.governance.agents.application;

/** Override alterado desde a última leitura do cliente; a API responde 409. */
public class AgentOverrideConflictException extends RuntimeException {

    public AgentOverrideConflictException() {
        super("O override do catálogo foi alterado por outra requisição. "
            + "Recarregue os dados e tente novamente.");
    }
}
