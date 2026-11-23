package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.agents.domain.AgentFlowVersion;

/** Fluxo + versão corrente (pode ser nula em estado inconsistente). */
public record AgentFlowDetail(AgentFlow flow, AgentFlowVersion currentVersion) {

    public AgentFlowDetail {
        if (flow == null) {
            throw new IllegalArgumentException("flow é obrigatório.");
        }
    }
}
