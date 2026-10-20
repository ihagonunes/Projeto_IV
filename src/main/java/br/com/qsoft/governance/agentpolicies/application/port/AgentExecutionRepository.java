package br.com.qsoft.governance.agentpolicies.application.port;

import br.com.qsoft.governance.agentpolicies.domain.AgentExecution;

/** Persistência da execução do agente (upsert por id). */
public interface AgentExecutionRepository {

    void save(AgentExecution execution);
}
