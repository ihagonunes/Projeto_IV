package br.com.qsoft.governance.agents.domain;

/** Porta de completion de um nó `agent` (adaptada ao AICompletionService). */
public interface AgentCompletionGateway {

    AgentCompletion complete(String agentKey, String systemPrompt, String userContent);
}
