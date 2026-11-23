package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.agents.domain.FlowEdge;
import br.com.qsoft.governance.agents.domain.FlowGraph;
import br.com.qsoft.governance.agents.domain.FlowNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Parse estrutural do grafo (não julga o DAG). Erros aqui são 400; violações
 * semânticas viram {@code FlowValidation} e podem ser persistidas como INVALID.
 * O `id` do nó é opaco do cliente e nunca vira identidade do servidor.
 */
@Component
public class AgentFlowGraphJson {

    static final int MAX_BYTES = 65_536;

    private final ObjectMapper objectMapper;

    public AgentFlowGraphJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FlowGraph parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("graphJson é obrigatório.");
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("graphJson excede o limite de 64 KB.");
        }
        JsonNode root = parseJson(raw);
        if (!root.isObject()) {
            throw new IllegalArgumentException("graphJson deve ser um objeto.");
        }
        JsonNode schemaVersion = root.path("schemaVersion");
        int schema = schemaVersion.isNumber() ? schemaVersion.asInt() : 0;
        return new FlowGraph(schema, parseNodes(root.path("nodes")), parseEdges(root.path("edges")));
    }

    private static List<FlowNode> parseNodes(JsonNode nodes) {
        if (nodes.isMissingNode() || nodes.isNull()) {
            return List.of();
        }
        if (!nodes.isArray()) {
            throw new IllegalArgumentException("graphJson.nodes deve ser um array.");
        }
        List<FlowNode> parsed = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode node : nodes.values()) {
            if (!node.isObject()) {
                throw new IllegalArgumentException("graphJson.nodes[] deve ser um objeto.");
            }
            JsonNode id = node.path("id");
            if (!id.isString() || id.asString().isBlank()) {
                throw new IllegalArgumentException("graphJson.nodes[].id é obrigatório.");
            }
            if (!ids.add(id.asString())) {
                throw new IllegalArgumentException("graphJson.nodes[].id deve ser único.");
            }
            JsonNode kind = node.path("kind");
            JsonNode agentKey = node.path("agentKey");
            JsonNode x = node.path("x");
            JsonNode y = node.path("y");
            parsed.add(new FlowNode(
                id.asString(),
                kind.isString() ? kind.asString() : "",
                agentKey.isString() ? agentKey.asString() : null,
                x.isNumber() ? x.asDouble() : null,
                y.isNumber() ? y.asDouble() : null));
        }
        return parsed;
    }

    private static List<FlowEdge> parseEdges(JsonNode edges) {
        if (edges.isMissingNode() || edges.isNull()) {
            return List.of();
        }
        if (!edges.isArray()) {
            throw new IllegalArgumentException("graphJson.edges deve ser um array.");
        }
        List<FlowEdge> parsed = new ArrayList<>();
        for (JsonNode edge : edges.values()) {
            if (!edge.isObject()) {
                throw new IllegalArgumentException("graphJson.edges[] deve ser um objeto.");
            }
            JsonNode from = edge.path("from");
            JsonNode to = edge.path("to");
            if (!from.isString() || from.asString().isBlank()
                || !to.isString() || to.asString().isBlank()) {
                throw new IllegalArgumentException("graphJson.edges[].from/to são obrigatórios.");
            }
            parsed.add(new FlowEdge(from.asString(), to.asString()));
        }
        return parsed;
    }

    private JsonNode parseJson(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("graphJson não é JSON válido.");
        }
    }
}
