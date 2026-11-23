package br.com.qsoft.governance.agents.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validação determinística do DAG (ADR-44 §8). O servidor é autoritativo: a
 * UI pode antecipar o resultado, mas o mesmo conjunto de regras vale no save,
 * na validação e na publicação.
 * <p>
 * Puro: sem Spring, Jackson, banco ou relógio.
 */
public final class FlowGraphValidator {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;
    public static final int MAX_NODES = 20;
    public static final int MAX_EDGES = 40;

    private static final String TASK = "task";
    private static final String AGENT = "agent";
    private static final String MERGE = "merge";
    private static final String RESULT = "result";
    private static final Set<String> KINDS = Set.of(TASK, AGENT, MERGE, RESULT);

    private FlowGraphValidator() {
    }

    public static FlowValidation validate(FlowGraph graph, int expectedSchemaVersion,
                                          Set<String> activeAgentKeys) {
        List<FlowValidationError> errors = new ArrayList<>();
        List<FlowNode> nodes = graph.nodes();
        List<FlowEdge> edges = graph.edges();

        if (expectedSchemaVersion != SUPPORTED_SCHEMA_VERSION
            || graph.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            errors.add(error("SCHEMA_VERSION_UNSUPPORTED",
                "schemaVersion não suportado; use " + SUPPORTED_SCHEMA_VERSION + ".",
                "/schemaVersion"));
        } else if (graph.schemaVersion() != expectedSchemaVersion) {
            errors.add(error("SCHEMA_VERSION_MISMATCH",
                "schemaVersion do grafo difere do informado.", "/schemaVersion"));
        }
        if (nodes.size() > MAX_NODES) {
            errors.add(error("NODE_LIMIT_EXCEEDED",
                "O fluxo excede " + MAX_NODES + " nós.", "/nodes"));
        }
        if (edges.size() > MAX_EDGES) {
            errors.add(error("EDGE_LIMIT_EXCEEDED",
                "O fluxo excede " + MAX_EDGES + " arestas.", "/edges"));
        }

        Map<String, FlowNode> byId = new LinkedHashMap<>();
        for (int index = 0; index < nodes.size(); index++) {
            FlowNode node = nodes.get(index);
            byId.putIfAbsent(node.id(), node);
            String base = "/nodes/" + index;
            if (!KINDS.contains(node.kind())) {
                errors.add(error("NODE_KIND_UNKNOWN",
                    "kind desconhecido: " + node.kind() + ".", base + "/kind"));
            }
            if (node.x() == null || node.y() == null
                || !Double.isFinite(node.x()) || !Double.isFinite(node.y())
                || node.x() < 0 || node.x() > 1 || node.y() < 0 || node.y() > 1) {
                errors.add(error("NODE_COORDINATES_INVALID",
                    "Coordenadas devem ser finitas em [0,1].", base));
            }
            if (AGENT.equals(node.kind())
                && (node.agentKey() == null || node.agentKey().isBlank()
                    || !activeAgentKeys.contains(node.agentKey()))) {
                errors.add(error("AGENT_KEY_UNKNOWN",
                    "agentKey inexistente, inativo ou não visível.", base + "/agentKey"));
            }
        }

        Set<String> edgeKeys = new HashSet<>();
        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> incoming = new HashMap<>();
        Map<String, Integer> outgoing = new HashMap<>();
        for (int index = 0; index < edges.size(); index++) {
            FlowEdge edge = edges.get(index);
            String path = "/edges/" + index;
            if (!byId.containsKey(edge.from()) || !byId.containsKey(edge.to())) {
                errors.add(error("EDGE_ENDPOINT_UNKNOWN",
                    "A aresta referencia um nó inexistente.", path));
                continue;
            }
            if (edge.from().equals(edge.to())) {
                errors.add(error("EDGE_SELF_LOOP", "Autoloop não é permitido.", path));
            }
            if (!edgeKeys.add(edge.from() + "\u0000" + edge.to())) {
                errors.add(error("EDGE_DUPLICATED", "Aresta duplicada.", path));
            }
            adjacency.computeIfAbsent(edge.from(), key -> new ArrayList<>()).add(edge.to());
            outgoing.merge(edge.from(), 1, Integer::sum);
            incoming.merge(edge.to(), 1, Integer::sum);
        }

        long tasks = nodes.stream().filter(node -> TASK.equals(node.kind())).count();
        long results = nodes.stream().filter(node -> RESULT.equals(node.kind())).count();
        if (tasks != 1) {
            errors.add(error("TASK_COUNT_INVALID", "O fluxo deve ter exatamente uma tarefa.",
                "/nodes"));
        }
        if (results != 1) {
            errors.add(error("RESULT_COUNT_INVALID",
                "O fluxo deve ter exatamente um resultado.", "/nodes"));
        }

        for (int index = 0; index < nodes.size(); index++) {
            FlowNode node = nodes.get(index);
            String base = "/nodes/" + index;
            int in = incoming.getOrDefault(node.id(), 0);
            int out = outgoing.getOrDefault(node.id(), 0);
            if (TASK.equals(node.kind()) && in > 0) {
                errors.add(error("TASK_HAS_INCOMING", "A tarefa não pode ter entrada.", base));
            }
            if (RESULT.equals(node.kind()) && out > 0) {
                errors.add(error("RESULT_HAS_OUTGOING", "O resultado não pode ter saída.", base));
            }
            if (MERGE.equals(node.kind()) && in < 2) {
                errors.add(error("MERGE_INSUFFICIENT_INPUTS",
                    "O consolidador precisa de ao menos 2 entradas.", base));
            }
            if (MERGE.equals(node.kind()) && out > 1) {
                errors.add(error("MERGE_MULTIPLE_OUTPUTS",
                    "O consolidador pode ter no máximo 1 saída.", base));
            }
            if (!RESULT.equals(node.kind()) && out == 0) {
                errors.add(error("NODE_WITHOUT_OUTPUT",
                    "Todo nó deve seguir para outra etapa ou para o resultado.", base));
            }
        }

        if (hasCycle(nodes, adjacency)) {
            errors.add(error("CYCLE", "O fluxo contém um ciclo.", "/edges"));
        }

        FlowNode task = nodes.stream()
            .filter(node -> TASK.equals(node.kind()))
            .findFirst()
            .orElse(null);
        if (task != null) {
            Set<String> reached = reachableFrom(task.id(), adjacency);
            for (int index = 0; index < nodes.size(); index++) {
                FlowNode node = nodes.get(index);
                if (!node.id().equals(task.id()) && !reached.contains(node.id())) {
                    errors.add(error("NODE_UNREACHABLE",
                        "O nó não é alcançável a partir da tarefa.", "/nodes/" + index));
                }
            }
            FlowNode result = nodes.stream()
                .filter(node -> RESULT.equals(node.kind()))
                .findFirst()
                .orElse(null);
            if (result != null && !reached.contains(result.id())) {
                errors.add(error("RESULT_UNREACHABLE",
                    "O resultado não é alcançável a partir da tarefa.", "/nodes"));
            }
        }

        boolean needsMerge = false;
        List<FlowNode> merges = nodes.stream()
            .filter(node -> MERGE.equals(node.kind())
                && incoming.getOrDefault(node.id(), 0) >= 2)
            .toList();
        for (int index = 0; index < nodes.size(); index++) {
            FlowNode node = nodes.get(index);
            if (outgoing.getOrDefault(node.id(), 0) <= 1) {
                continue;
            }
            boolean consolidated = merges.stream()
                .anyMatch(merge -> allPathsMeet(node.id(), merge.id(), adjacency));
            if (!consolidated) {
                needsMerge = true;
                errors.add(error("BRANCH_NOT_CONSOLIDATED",
                    "Há ramos paralelos sem consolidação por merge.",
                    "/nodes/" + index));
            }
        }

        return new FlowValidation(errors.isEmpty(), needsMerge, errors);
    }

    private static boolean hasCycle(List<FlowNode> nodes, Map<String, List<String>> adjacency) {
        Map<String, Integer> state = new HashMap<>();
        for (FlowNode node : nodes) {
            if (visit(node.id(), adjacency, state)) {
                return true;
            }
        }
        return false;
    }

    private static boolean visit(String id, Map<String, List<String>> adjacency,
                                 Map<String, Integer> state) {
        Integer current = state.get(id);
        if (current != null && current == 1) {
            return true;
        }
        if (current != null && current == 2) {
            return false;
        }
        state.put(id, 1);
        for (String next : adjacency.getOrDefault(id, List.of())) {
            if (visit(next, adjacency, state)) {
                return true;
            }
        }
        state.put(id, 2);
        return false;
    }

    private static Set<String> reachableFrom(String start, Map<String, List<String>> adjacency) {
        Set<String> reached = new HashSet<>();
        ArrayList<String> stack = new ArrayList<>();
        stack.add(start);
        while (!stack.isEmpty()) {
            String id = stack.remove(stack.size() - 1);
            if (reached.add(id)) {
                stack.addAll(adjacency.getOrDefault(id, List.of()));
            }
        }
        return reached;
    }

    /** Todos os caminhos a partir de `start` passam por `mergeId`? */
    private static boolean allPathsMeet(String start, String mergeId,
                                        Map<String, List<String>> adjacency) {
        return allPathsMeet(start, mergeId, adjacency, new HashMap<>());
    }

    private static boolean allPathsMeet(String id, String mergeId,
                                        Map<String, List<String>> adjacency,
                                        Map<String, Boolean> memo) {
        if (id.equals(mergeId)) {
            return true;
        }
        Boolean cached = memo.get(id);
        if (cached != null) {
            return cached;
        }
        List<String> next = adjacency.getOrDefault(id, List.of());
        memo.put(id, false);
        boolean meets = !next.isEmpty()
            && next.stream().allMatch(target -> allPathsMeet(target, mergeId, adjacency, memo));
        memo.put(id, meets);
        return meets;
    }

    private static FlowValidationError error(String code, String message, String path) {
        return new FlowValidationError(code, message, path);
    }
}
