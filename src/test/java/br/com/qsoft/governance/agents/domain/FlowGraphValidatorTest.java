package br.com.qsoft.governance.agents.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FlowGraphValidatorTest {

    private static final Set<String> ACTIVE = Set.of("policies", "writing", "data");

    @Test
    void acceptsLinearFlowNormalizedByConsolidate() {
        FlowValidation result = validate(graph(1,
            List.of(task("t"), agent("a", "policies"), result("r")),
            List.of(edge("t", "a"), edge("a", "r"))));

        assertThat(result.errors()).isEmpty();
        assertThat(result.valid()).isTrue();
        assertThat(result.needsMerge()).isFalse();
    }

    @Test
    void acceptsFanOutConsolidatedByMerge() {
        FlowValidation result = validate(graph(1,
            List.of(task("t"), agent("a", "policies"), agent("b", "writing"), merge("m"),
                result("r")),
            List.of(edge("t", "a"), edge("t", "b"), edge("a", "m"), edge("b", "m"),
                edge("m", "r"))));

        assertThat(result.errors()).isEmpty();
        assertThat(result.valid()).isTrue();
    }

    @Test
    void directBranchesToResultRequireMerge() {
        FlowValidation result = validate(graph(1,
            List.of(task("t"), agent("a", "policies"), agent("b", "writing"), result("r")),
            List.of(edge("t", "a"), edge("t", "b"), edge("a", "r"), edge("b", "r"))));

        assertThat(result.valid()).isFalse();
        assertThat(result.needsMerge()).isTrue();
        assertThat(codes(result)).contains("BRANCH_NOT_CONSOLIDATED");
    }

    @Test
    void requiresExactlyOneTaskAndOneResult() {
        assertThat(codes(validate(graph(1,
            List.of(agent("a", "policies"), result("r")), List.of(edge("a", "r"))))))
            .contains("TASK_COUNT_INVALID");
        assertThat(codes(validate(graph(1,
            List.of(task("t"), task("t2"), agent("a", "policies")),
            List.of(edge("t", "a"), edge("t2", "a"))))))
            .contains("TASK_COUNT_INVALID");
        assertThat(codes(validate(graph(1,
            List.of(task("t"), agent("a", "policies")), List.of(edge("t", "a"))))))
            .contains("RESULT_COUNT_INVALID");
        assertThat(codes(validate(graph(1,
            List.of(task("t"), result("r"), result("r2")),
            List.of(edge("t", "r"), edge("t", "r2"))))))
            .contains("RESULT_COUNT_INVALID");
    }

    @Test
    void taskHasNoInputAndResultHasNoOutput() {
        assertThat(codes(validate(graph(1,
            List.of(task("t"), agent("a", "policies"), result("r")),
            List.of(edge("a", "t"), edge("t", "a"), edge("a", "r"))))))
            .contains("TASK_HAS_INCOMING");
        assertThat(codes(validate(graph(1,
            List.of(task("t"), agent("a", "policies"), result("r")),
            List.of(edge("t", "a"), edge("a", "r"), edge("r", "a"))))))
            .contains("RESULT_HAS_OUTGOING");
    }

    @Test
    void detectsCycleSelfLoopAndDuplicateEdge() {
        assertThat(codes(validate(graph(1,
            List.of(task("t"), agent("a", "policies"), agent("b", "writing"), result("r")),
            List.of(edge("t", "a"), edge("a", "b"), edge("b", "a"), edge("b", "r"))))))
            .contains("CYCLE");
        assertThat(codes(validate(graph(1,
            List.of(task("t"), agent("a", "policies"), result("r")),
            List.of(edge("t", "a"), edge("a", "a"), edge("a", "r"))))))
            .contains("EDGE_SELF_LOOP");
        assertThat(codes(validate(graph(1,
            List.of(task("t"), agent("a", "policies"), result("r")),
            List.of(edge("t", "a"), edge("t", "a"), edge("a", "r"))))))
            .contains("EDGE_DUPLICATED");
    }

    @Test
    void edgeMustReferenceExistingNodes() {
        assertThat(codes(validate(graph(1,
            List.of(task("t"), agent("a", "policies"), result("r")),
            List.of(edge("t", "ghost"), edge("a", "r"))))))
            .contains("EDGE_ENDPOINT_UNKNOWN");
    }

    @Test
    void rejectsOrphansAndResultUnreachable() {
        assertThat(codes(validate(graph(1,
            List.of(task("t"), result("r"), agent("loose", "policies")),
            List.of(edge("t", "r"))))))
            .contains("NODE_UNREACHABLE");
        assertThat(codes(validate(graph(1,
            List.of(task("t"), agent("a", "policies"), result("r")),
            List.of(edge("a", "r"))))))
            .contains("RESULT_UNREACHABLE", "NODE_WITHOUT_OUTPUT");
    }

    @Test
    void nodeWithoutOutputIsRejected() {
        FlowValidation result = validate(graph(1,
            List.of(task("t"), agent("a", "policies"), agent("b", "writing"), result("r")),
            List.of(edge("t", "b"), edge("b", "r"), edge("t", "a"))));

        assertThat(codes(result)).contains("NODE_WITHOUT_OUTPUT");
    }

    @Test
    void mergeNeedsAtLeastTwoInputsAndAtMostOneOutput() {
        assertThat(codes(validate(graph(1,
            List.of(task("t"), agent("a", "policies"), merge("m"), result("r")),
            List.of(edge("t", "a"), edge("a", "m"), edge("m", "r"))))))
            .contains("MERGE_INSUFFICIENT_INPUTS");
        assertThat(codes(validate(graph(1,
            List.of(task("t"), agent("a", "policies"), agent("b", "writing"), merge("m"),
                result("r"), result("r2")),
            List.of(edge("t", "a"), edge("t", "b"), edge("a", "m"), edge("b", "m"),
                edge("m", "r"), edge("m", "r2"))))))
            .contains("MERGE_MULTIPLE_OUTPUTS", "RESULT_COUNT_INVALID");
    }

    @Test
    void rejectsUnknownKindAndInvalidCoordinates() {
        assertThat(codes(validate(graph(1,
            List.of(task("t"), node("x", "robot", null, 0.5, 0.5), result("r")),
            List.of(edge("t", "x"), edge("x", "r"))))))
            .contains("NODE_KIND_UNKNOWN");

        assertThat(codes(validate(graph(1,
            List.of(task("t"), node("a", "agent", "policies", 1.5, 0.5), result("r")),
            List.of(edge("t", "a"), edge("a", "r"))))))
            .contains("NODE_COORDINATES_INVALID");
        assertThat(codes(validate(graph(1,
            List.of(task("t"), node("a", "agent", "policies", null, 0.5), result("r")),
            List.of(edge("t", "a"), edge("a", "r"))))))
            .contains("NODE_COORDINATES_INVALID");
    }

    @Test
    void agentKeyMustBeActiveAndVisible() {
        assertThat(codes(validate(graph(1,
            List.of(task("t"), agent("a", "merge"), result("r")),
            List.of(edge("t", "a"), edge("a", "r"))))))
            .contains("AGENT_KEY_UNKNOWN");
        assertThat(codes(validate(graph(1,
            List.of(task("t"), agent("a", "finance"), result("r")),
            List.of(edge("t", "a"), edge("a", "r"))))))
            .contains("AGENT_KEY_UNKNOWN");
        assertThat(codes(validate(graph(1,
            List.of(task("t"), node("a", "agent", null, 0.5, 0.5), result("r")),
            List.of(edge("t", "a"), edge("a", "r"))))))
            .contains("AGENT_KEY_UNKNOWN");
    }

    @Test
    void enforcesSchemaAndSizeLimits() {
        assertThat(codes(validate(graph(2,
            List.of(task("t"), agent("a", "policies"), result("r")),
            List.of(edge("t", "a"), edge("a", "r"))))))
            .contains("SCHEMA_VERSION_UNSUPPORTED");

        List<FlowNode> many = new ArrayList<>();
        many.add(task("t"));
        for (int i = 0; i < 20; i++) {
            many.add(agent("a" + i, "policies"));
        }
        many.add(result("r"));
        List<FlowEdge> edges = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            edges.add(edge("t", "a" + i));
        }
        assertThat(codes(validate(graph(1, many, edges))))
            .contains("NODE_LIMIT_EXCEEDED");
    }

    private FlowValidation validate(FlowGraph graph) {
        return FlowGraphValidator.validate(graph, 1, ACTIVE);
    }

    private static List<String> codes(FlowValidation validation) {
        return validation.errors().stream().map(FlowValidationError::code).toList();
    }

    private static FlowGraph graph(int schemaVersion, List<FlowNode> nodes,
                                   List<FlowEdge> edges) {
        return new FlowGraph(schemaVersion, nodes, edges);
    }

    private static FlowNode task(String id) {
        return node(id, "task", null, 0.0, 0.0);
    }

    private static FlowNode agent(String id, String agentKey) {
        return node(id, "agent", agentKey, 0.5, 0.5);
    }

    private static FlowNode merge(String id) {
        return node(id, "merge", null, 0.5, 0.5);
    }

    private static FlowNode result(String id) {
        return node(id, "result", null, 1.0, 1.0);
    }

    private static FlowNode node(String id, String kind, String agentKey, Double x, Double y) {
        return new FlowNode(id, kind, agentKey, x, y);
    }

    private static FlowEdge edge(String from, String to) {
        return new FlowEdge(from, to);
    }
}
