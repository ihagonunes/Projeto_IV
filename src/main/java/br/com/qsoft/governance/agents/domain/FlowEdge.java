package br.com.qsoft.governance.agents.domain;

/** Aresta direcionada entre dois nós do grafo. */
public record FlowEdge(String from, String to) {

    public FlowEdge {
        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            throw new IllegalArgumentException("from/to da aresta são obrigatórios.");
        }
    }
}
