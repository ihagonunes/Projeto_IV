package br.com.qsoft.governance.agents.domain;

import java.util.List;
import java.util.Objects;

/** Grafo do fluxo já tipado; a validação semântica é do {@link FlowGraphValidator}. */
public record FlowGraph(int schemaVersion, List<FlowNode> nodes, List<FlowEdge> edges) {

    public FlowGraph {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        for (FlowNode node : nodes) {
            Objects.requireNonNull(node, "node é obrigatório.");
        }
        for (FlowEdge edge : edges) {
            Objects.requireNonNull(edge, "edge é obrigatório.");
        }
    }
}
