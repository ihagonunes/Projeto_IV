package br.com.qsoft.governance.agents.application;

/**
 * Catálogo/fluxo inexistente para o tenant autenticado. Na API vira 404 — o
 * mesmo status de um recurso de outro tenant (não distinguir é intencional).
 */
public class AgentCatalogEntryNotFoundException extends RuntimeException {

    public AgentCatalogEntryNotFoundException(String agentKey) {
        super("Agente não encontrado: " + agentKey + ".");
    }

    public AgentCatalogEntryNotFoundException(String agentKey, int version) {
        super("Agente não encontrado: " + agentKey + " (versão " + version + ").");
    }
}
