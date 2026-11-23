package br.com.qsoft.governance.agents.domain;

/** Falha de uma etapa; carrega apenas um código estável. */
public class AgentFlowFailureException extends RuntimeException {

    private final String code;

    public AgentFlowFailureException(String code) {
        super(code);
        this.code = code == null || code.isBlank() ? "AGENT_COMPLETION_FAILED" : code;
    }

    public String code() {
        return code;
    }
}
