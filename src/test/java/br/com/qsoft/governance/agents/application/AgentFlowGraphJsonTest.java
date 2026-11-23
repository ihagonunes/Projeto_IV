package br.com.qsoft.governance.agents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.agents.domain.FlowGraph;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentFlowGraphJsonTest {

    private final AgentFlowGraphJson graphJson = new AgentFlowGraphJson(new ObjectMapper());

    @Test
    void parsesGraphWithOpaqueNodeIdsAndCoordinates() {
        FlowGraph graph = graphJson.parse("""
            {"schemaVersion":1,"nodes":[
              {"id":"step-1","kind":"task","x":0.1,"y":0.2},
              {"id":"c7a6f1c2-0b1f-4a55-9f5f-6d5a1e7d3b10","kind":"result","x":0.9,"y":0.8}
            ],"edges":[{"from":"step-1","to":"c7a6f1c2-0b1f-4a55-9f5f-6d5a1e7d3b10"}]}
            """);

        assertThat(graph.schemaVersion()).isEqualTo(1);
        assertThat(graph.nodes()).hasSize(2);
        assertThat(graph.nodes().get(0).id()).isEqualTo("step-1");
        assertThat(graph.nodes().get(0).x()).isEqualTo(0.1);
        assertThat(graph.edges()).singleElement()
            .satisfies(edge -> assertThat(edge.from()).isEqualTo("step-1"));
    }

    @Test
    void rejectsNonObjectInvalidJsonAndNonArrayCollections() {
        assertThatThrownBy(() -> graphJson.parse(null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> graphJson.parse(" "))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> graphJson.parse("[]"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> graphJson.parse("não é json"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> graphJson.parse("{\"nodes\":{}}"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> graphJson.parse("{\"edges\":{}}"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNodeWithoutIdAndDuplicateId() {
        assertThatThrownBy(() -> graphJson.parse("{\"nodes\":[{\"kind\":\"task\"}]}"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> graphJson.parse("{\"nodes\":[{\"id\":\"a\"},{\"id\":\"a\"}]}"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEdgeWithoutEndpoints() {
        assertThatThrownBy(() -> graphJson.parse("{\"edges\":[{\"from\":\"a\"}]}"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsUnknownKindForSemanticValidation() {
        FlowGraph graph = graphJson.parse("{\"nodes\":[{\"id\":\"a\",\"kind\":\"robot\"}]}");

        assertThat(graph.nodes().get(0).kind()).isEqualTo("robot");
    }

    @Test
    void rejectsGraphAboveTheSizeLimit() {
        String filler = "x".repeat(70_000);
        String raw = "{\"schemaVersion\":1,\"nodes\":[],\"edges\":[],\"extra\":\"" + filler + "\"}";

        assertThatThrownBy(() -> graphJson.parse(raw))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
