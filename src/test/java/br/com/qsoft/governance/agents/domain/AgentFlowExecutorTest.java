package br.com.qsoft.governance.agents.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AgentFlowExecutorTest {

    private static final ExecutionLimits LIMITS = new ExecutionLimits(2,
        Duration.ofSeconds(2), Duration.ofSeconds(5));
    private final AgentFlowExecutor executor = new AgentFlowExecutor();

    @Test
    void executesLinearFlowAndUsesTheAgentCompletion() {
        FlowGraph graph = graph(List.of(task("t"), agent("a", "policies"), result("r")),
            List.of(edge("t", "a"), edge("a", "r")));
        RecordingGateway gateway = new RecordingGateway("[stub] resposta");

        FlowExecutionResult result = executor.execute(graph, "Tarefa do usuário",
            Map.of("policies", "Prompt de políticas"), gateway, LIMITS);

        assertThat(result.status()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(result.result()).isEqualTo("[stub] resposta");
        assertThat(gateway.calls).hasSize(1);
        assertThat(gateway.lastContent())
            .contains("Tarefa do usuário").contains("Prompt de políticas");
        assertThat(result.steps()).extracting(FlowStepResult::status)
            .containsOnly(ExecutionStepStatus.SUCCEEDED);
        assertThat(result.steps()).extracting(FlowStepResult::nodeId)
            .containsExactly("t", "a", "r");
    }

    @Test
    void fanOutIsConcatenatedByMergeInStableEdgeOrder() {
        FlowGraph graph = graph(
            List.of(task("t"), agent("a", "policies"), agent("b", "writing"), merge("m"),
                result("r")),
            List.of(edge("t", "a"), edge("t", "b"), edge("a", "m"), edge("b", "m"),
                edge("m", "r")));
        RecordingGateway gateway = new RecordingGateway(null);

        FlowExecutionResult result = executor.execute(graph, "Tarefa",
            Map.of("policies", "P-A", "writing", "P-B"), gateway, LIMITS);

        assertThat(result.status()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(result.result()).isEqualTo("out-a\n\nout-b");
        assertThat(result.steps()).extracting(FlowStepResult::nodeId)
            .containsExactly("t", "a", "b", "m", "r");
    }

    @Test
    void failFastMarksTheFailedStepAndSkipsTheRest() {
        FlowGraph graph = graph(
            List.of(task("t"), agent("a", "policies"), agent("b", "writing"), merge("m"),
                result("r")),
            List.of(edge("t", "a"), edge("t", "b"), edge("a", "m"), edge("b", "m"),
                edge("m", "r")));
        RecordingGateway gateway = new RecordingGateway(null);
        gateway.failOn("writing", "HTTP_500");

        FlowExecutionResult result = executor.execute(graph, "Tarefa",
            Map.of("policies", "P-A", "writing", "P-B"), gateway, LIMITS);

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.result()).isNull();
        assertThat(result.errorCode()).isEqualTo("HTTP_500");
        assertThat(result.steps()).filteredOn(step -> step.status() == ExecutionStepStatus.FAILED)
            .extracting(FlowStepResult::nodeId).contains("b");
        assertThat(result.steps()).filteredOn(step -> step.nodeId().equals("m"))
            .extracting(FlowStepResult::status).containsExactly(ExecutionStepStatus.SKIPPED);
    }

    @Test
    void stepTimeoutIsReportedAsTimedOut() {
        FlowGraph graph = graph(List.of(task("t"), agent("a", "policies"), result("r")),
            List.of(edge("t", "a"), edge("a", "r")));
        RecordingGateway gateway = new RecordingGateway("ok");
        gateway.sleepMillis("policies", 500);
        ExecutionLimits limits = new ExecutionLimits(2, Duration.ofMillis(50),
            Duration.ofSeconds(5));

        FlowExecutionResult result = executor.execute(graph, "Tarefa",
            Map.of("policies", "P"), gateway, limits);

        assertThat(result.status()).isEqualTo(ExecutionStatus.TIMED_OUT);
        assertThat(result.errorCode()).isEqualTo("STEP_TIMEOUT");
        assertThat(result.steps()).filteredOn(step -> step.nodeId().equals("a"))
            .extracting(FlowStepResult::status).containsExactly(ExecutionStepStatus.TIMED_OUT);
    }

    @Test
    void totalTimeoutCapsTheExecution() {
        FlowGraph graph = graph(List.of(task("t"), agent("a", "policies"), result("r")),
            List.of(edge("t", "a"), edge("a", "r")));
        RecordingGateway gateway = new RecordingGateway("ok");
        gateway.sleepMillis("policies", 300);
        ExecutionLimits limits = new ExecutionLimits(2, Duration.ofSeconds(5),
            Duration.ofMillis(50));

        FlowExecutionResult result = executor.execute(graph, "Tarefa",
            Map.of("policies", "P"), gateway, limits);

        assertThat(result.status()).isEqualTo(ExecutionStatus.TIMED_OUT);
        assertThat(result.errorCode()).isEqualTo("TOTAL_TIMEOUT");
    }

    @Test
    void missingPromptFailsWithoutCallingTheGateway() {
        FlowGraph graph = graph(List.of(task("t"), agent("a", "policies"), result("r")),
            List.of(edge("t", "a"), edge("a", "r")));
        RecordingGateway gateway = new RecordingGateway("ok");

        FlowExecutionResult result = executor.execute(graph, "Tarefa", Map.of(), gateway, LIMITS);

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo("AGENT_PROMPT_MISSING");
        assertThat(gateway.calls).isEmpty();
    }

    @Test
    void cyclicGraphIsRejectedBeforeExecution() {
        FlowGraph graph = graph(List.of(task("t"), agent("a", "policies"), agent("b", "writing"),
                result("r")),
            List.of(edge("t", "a"), edge("a", "b"), edge("b", "a"), edge("b", "r")));

        FlowExecutionResult result = executor.execute(graph, "Tarefa",
            Map.of("policies", "P", "writing", "W"), new RecordingGateway("ok"), LIMITS);

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo("INVALID_GRAPH");
    }

    private static FlowGraph graph(List<FlowNode> nodes, List<FlowEdge> edges) {
        return new FlowGraph(1, nodes, edges);
    }

    private static FlowNode task(String id) {
        return node(id, "task", null);
    }

    private static FlowNode agent(String id, String key) {
        return node(id, "agent", key);
    }

    private static FlowNode merge(String id) {
        return node(id, "merge", null);
    }

    private static FlowNode result(String id) {
        return node(id, "result", null);
    }

    private static FlowNode node(String id, String kind, String agentKey) {
        return new FlowNode(id, kind, agentKey, 0.5, 0.5);
    }

    private static FlowEdge edge(String from, String to) {
        return new FlowEdge(from, to);
    }

    private static final class RecordingGateway implements AgentCompletionGateway {

        private final Map<String, String> calls = new ConcurrentHashMap<>();
        private final Map<String, Integer> sleeps = new ConcurrentHashMap<>();
        private final Map<String, String> failures = new ConcurrentHashMap<>();
        private final String content;
        private final AtomicInteger sequence = new AtomicInteger();
        private volatile String lastContent;

        RecordingGateway(String content) {
            this.content = content;
        }

        void failOn(String agentKey, String code) {
            failures.put(agentKey, code);
        }

        void sleepMillis(String agentKey, long millis) {
            sleeps.put(agentKey, (int) millis);
        }

        String lastContent() {
            return lastContent;
        }

        @Override
        public AgentCompletion complete(String agentKey, String systemPrompt, String userContent) {
            lastContent = systemPrompt + "\n" + userContent;
            Integer sleep = sleeps.get(agentKey);
            if (sleep != null) {
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new AgentFlowTimeoutException();
                }
            }
            String failure = failures.get(agentKey);
            if (failure != null) {
                throw new AgentFlowFailureException(failure);
            }
            String output = content != null ? content
                : "policies".equals(agentKey) ? "out-a" : "out-b";
            calls.put(agentKey, output);
            return new AgentCompletion(UUID.randomUUID(), output);
        }
    }
}
