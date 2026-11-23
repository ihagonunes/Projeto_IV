package br.com.qsoft.governance.agents.domain;

/**
 * Nó do grafo. `kind` é textual para permitir reportar `NODE_KIND_UNKNOWN`
 * em vez de falhar no parse; `agentKey` só existe em nós `agent`.
 */
public record FlowNode(String id, String kind, String agentKey, Double x, Double y) {

    public FlowNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id do nó é obrigatório.");
        }
        kind = kind == null ? "" : kind;
    }
}
