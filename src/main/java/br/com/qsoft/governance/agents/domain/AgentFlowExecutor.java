package br.com.qsoft.governance.agents.domain;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Motor topológico determinístico do MVP (ADR-45): níveis topológicos,
 * fail-fast, `merge`/`result` por concatenação em ordem estável de arestas,
 * paralelismo limitado e timeout por etapa/total. Não acessa banco, Spring ou
 * provider: recebe a porta de completion e os prompts já resolvidos.
 */
public final class AgentFlowExecutor {

    private static final String TASK = "task";
    private static final String AGENT = "agent";
    private static final String MERGE = "merge";
    private static final String RESULT = "result";

    public FlowExecutionResult execute(FlowGraph graph, String input,
                                       Map<String, String> prompts,
                                       AgentCompletionGateway gateway,
                                       ExecutionLimits limits) {
        Objects.requireNonNull(graph, "graph é obrigatório.");
        Objects.requireNonNull(gateway, "gateway é obrigatório.");
        Objects.requireNonNull(limits, "limits são obrigatórios.");
        List<FlowNode> nodes = graph.nodes();
        Map<String, FlowNode> byId = new LinkedHashMap<>();
        nodes.forEach(node -> byId.putIfAbsent(node.id(), node));
        Map<String, List<String>> upstream = upstreamByTarget(graph.edges());
        List<List<FlowNode>> levels = topologicalLevels(nodes, graph.edges());
        if (levels == null) {
            return new FlowExecutionResult(ExecutionStatus.FAILED, null, "INVALID_GRAPH",
                skeleton(nodes, ExecutionStepStatus.SKIPPED, null));
        }

        Map<String, FlowStepResult> steps = new LinkedHashMap<>();
        nodes.forEach(node -> steps.put(node.id(),
            new FlowStepResult(node.id(), node.kind(), ExecutionStepStatus.PENDING, null, null,
                null, null)));
        Map<String, String> outputs = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(limits.maxParallelism());
        long deadline = System.nanoTime() + limits.totalTimeout().toNanos();
        try {
            for (List<FlowNode> level : levels) {
                if (System.nanoTime() >= deadline) {
                    return timedOut(steps, outputs, null, "TOTAL_TIMEOUT");
                }
                List<Future<NodeOutcome>> futures = new ArrayList<>();
                for (FlowNode node : level) {
                    futures.add(pool.submit(() -> runNode(node, input, prompts, gateway, outputs,
                        upstream.getOrDefault(node.id(), List.of()))));
                }
                for (int index = 0; index < level.size(); index++) {
                    FlowNode node = level.get(index);
                    Future<NodeOutcome> future = futures.get(index);
                    long remaining = deadline - System.nanoTime();
                    long wait = Math.min(remaining, limits.stepTimeout().toNanos());
                    if (wait <= 0) {
                        cancelAll(futures);
                        return timedOut(steps, outputs, node.id(), "TOTAL_TIMEOUT");
                    }
                    try {
                        NodeOutcome outcome = future.get(wait, TimeUnit.NANOSECONDS);
                        steps.put(node.id(), outcome.step());
                        if (outcome.step().status() == ExecutionStepStatus.SUCCEEDED) {
                            outputs.put(node.id(), outcome.output());
                        } else {
                            cancelAll(futures);
                            markSkipped(steps, outputs);
                            boolean timeout = outcome.step().status() == ExecutionStepStatus.TIMED_OUT;
                            return new FlowExecutionResult(
                                timeout ? ExecutionStatus.TIMED_OUT : ExecutionStatus.FAILED,
                                null, outcome.step().errorCode(), List.copyOf(steps.values()));
                        }
                    } catch (TimeoutException ex) {
                        future.cancel(true);
                        String code = remaining < limits.stepTimeout().toNanos()
                            ? "TOTAL_TIMEOUT" : "STEP_TIMEOUT";
                        steps.put(node.id(), terminal(node, ExecutionStepStatus.TIMED_OUT,
                            null, code));
                        cancelAll(futures);
                        markSkipped(steps, outputs);
                        return new FlowExecutionResult(ExecutionStatus.TIMED_OUT, null,
                            code, List.copyOf(steps.values()));
                    } catch (ExecutionException ex) {
                        Throwable cause = ex.getCause();
                        ExecutionStepStatus status = cause instanceof AgentFlowTimeoutException
                            ? ExecutionStepStatus.TIMED_OUT : ExecutionStepStatus.FAILED;
                        String code = cause instanceof AgentFlowFailureException failure
                            ? failure.code()
                            : status == ExecutionStepStatus.TIMED_OUT
                                ? "STEP_TIMEOUT" : "AGENT_COMPLETION_FAILED";
                        steps.put(node.id(), terminal(node, status, null, code));
                        cancelAll(futures);
                        markSkipped(steps, outputs);
                        return new FlowExecutionResult(
                            status == ExecutionStepStatus.TIMED_OUT
                                ? ExecutionStatus.TIMED_OUT : ExecutionStatus.FAILED,
                            null, code, List.copyOf(steps.values()));
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        cancelAll(futures);
                        markSkipped(steps, outputs);
                        return new FlowExecutionResult(ExecutionStatus.CANCELLED, null,
                            "CANCELLED", List.copyOf(steps.values()));
                    }
                }
            }
        } finally {
            pool.shutdownNow();
        }

        FlowNode resultNode = nodes.stream()
            .filter(node -> RESULT.equals(node.kind()))
            .findFirst()
            .orElse(null);
        String result = resultNode == null ? null : outputs.get(resultNode.id());
        if (result == null) {
            markSkipped(steps, outputs);
            return new FlowExecutionResult(ExecutionStatus.FAILED, null, "RESULT_NOT_REACHED",
                List.copyOf(steps.values()));
        }
        return new FlowExecutionResult(ExecutionStatus.SUCCEEDED, result, null,
            List.copyOf(steps.values()));
    }

    private static NodeOutcome runNode(FlowNode node, String input, Map<String, String> prompts,
                                       AgentCompletionGateway gateway, Map<String, String> outputs,
                                       List<String> upstreamIds) {
        Instant startedAt = Instant.now();
        if (TASK.equals(node.kind())) {
            return new NodeOutcome(step(node, ExecutionStepStatus.SUCCEEDED, null, null,
                startedAt), input);
        }
        String context = upstreamIds.stream()
            .map(outputs::get)
            .filter(Objects::nonNull)
            .collect(Collectors.joining("\n\n"));
        if (MERGE.equals(node.kind()) || RESULT.equals(node.kind())) {
            return new NodeOutcome(step(node, ExecutionStepStatus.SUCCEEDED, null, null,
                startedAt), context);
        }
        if (AGENT.equals(node.kind())) {
            String prompt = prompts.get(node.agentKey());
            if (prompt == null || prompt.isBlank()) {
                return new NodeOutcome(step(node, ExecutionStepStatus.FAILED, null,
                    "AGENT_PROMPT_MISSING", startedAt), null);
            }
            String content = context.isEmpty()
                ? input
                : input + "\n\nContexto das etapas anteriores:\n" + context;
            AgentCompletion completion = gateway.complete(node.agentKey(), prompt, content);
            if (completion.content().isBlank()) {
                // Preserve the receipt: the provider may have charged for this call.
                return new NodeOutcome(step(node, ExecutionStepStatus.FAILED,
                    completion.requestId(), "AGENT_EMPTY_RESPONSE", startedAt), null);
            }
            return new NodeOutcome(step(node, ExecutionStepStatus.SUCCEEDED,
                completion.requestId(), null, startedAt), completion.content());
        }
        return new NodeOutcome(step(node, ExecutionStepStatus.FAILED, null, "UNKNOWN_KIND",
            startedAt), null);
    }

    private static FlowStepResult step(FlowNode node, ExecutionStepStatus status, UUID requestId,
                                       String errorCode, Instant startedAt) {
        return new FlowStepResult(node.id(), node.kind(), status, requestId, errorCode, startedAt,
            Instant.now());
    }

    private static FlowStepResult terminal(FlowNode node, ExecutionStepStatus status,
                                           UUID requestId, String errorCode) {
        return new FlowStepResult(node.id(), node.kind(), status, requestId, errorCode,
            Instant.now(), Instant.now());
    }

    private static void markSkipped(Map<String, FlowStepResult> steps, Map<String, String> outputs) {
        steps.replaceAll((id, step) -> step.status() == ExecutionStepStatus.PENDING
            ? new FlowStepResult(step.nodeId(), step.nodeKind(), ExecutionStepStatus.SKIPPED,
                null, null, Instant.now(), Instant.now())
            : step);
    }

    private static FlowExecutionResult timedOut(Map<String, FlowStepResult> steps,
                                                Map<String, String> outputs, String nodeId,
                                                String code) {
        if (nodeId != null) {
            FlowStepResult current = steps.get(nodeId);
            steps.put(nodeId, new FlowStepResult(current.nodeId(), current.nodeKind(),
                ExecutionStepStatus.TIMED_OUT, null, code, Instant.now(), Instant.now()));
        }
        markSkipped(steps, outputs);
        return new FlowExecutionResult(ExecutionStatus.TIMED_OUT, null, code,
            List.copyOf(steps.values()));
    }

    private static void cancelAll(List<Future<NodeOutcome>> futures) {
        futures.forEach(future -> future.cancel(true));
    }

    private static List<FlowStepResult> skeleton(List<FlowNode> nodes, ExecutionStepStatus status,
                                                 String errorCode) {
        return nodes.stream()
            .map(node -> new FlowStepResult(node.id(), node.kind(), status, null, errorCode,
                null, null))
            .toList();
    }

    private static Map<String, List<String>> upstreamByTarget(List<FlowEdge> edges) {
        Map<String, List<String>> upstream = new HashMap<>();
        for (FlowEdge edge : edges) {
            upstream.computeIfAbsent(edge.to(), key -> new ArrayList<>()).add(edge.from());
        }
        return upstream;
    }

    private static List<List<FlowNode>> topologicalLevels(List<FlowNode> nodes, List<FlowEdge> edges) {
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        for (FlowNode node : nodes) {
            inDegree.putIfAbsent(node.id(), 0);
        }
        for (FlowEdge edge : edges) {
            if (!inDegree.containsKey(edge.from()) || !inDegree.containsKey(edge.to())) {
                return null;
            }
            outgoing.computeIfAbsent(edge.from(), key -> new ArrayList<>()).add(edge.to());
            inDegree.merge(edge.to(), 1, Integer::sum);
        }
        Deque<String> ready = new ArrayDeque<>();
        nodes.stream().filter(node -> inDegree.get(node.id()) == 0)
            .forEach(node -> ready.add(node.id()));
        List<List<FlowNode>> levels = new ArrayList<>();
        int processed = 0;
        while (!ready.isEmpty()) {
            List<String> current = new ArrayList<>(ready);
            ready.clear();
            List<FlowNode> level = new ArrayList<>();
            for (String id : current) {
                FlowNode node = nodes.stream().filter(candidate -> candidate.id().equals(id))
                    .findFirst().orElseThrow();
                level.add(node);
                processed++;
                for (String next : outgoing.getOrDefault(id, List.of())) {
                    if (inDegree.merge(next, -1, Integer::sum) == 0) {
                        ready.add(next);
                    }
                }
            }
            levels.add(level);
        }
        return processed == nodes.size() ? levels : null;
    }

    private record NodeOutcome(FlowStepResult step, String output) {
    }
}
