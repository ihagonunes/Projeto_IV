package br.com.qsoft.governance.agents.infrastructure.web;

import br.com.qsoft.governance.agents.application.AgentFlowDetail;
import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Converte o grafo persistido (texto JSONB) em JSON de resposta. */
@Component
public class AgentFlowResponseMapper {

    private final ObjectMapper objectMapper;

    public AgentFlowResponseMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String writeGraph(JsonNode graph) {
        try {
            return objectMapper.writeValueAsString(graph);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("graphJson inválido.");
        }
    }

    public AgentFlowDetailResponse detail(AgentFlowDetail detail) {
        AgentFlowVersionResponse version = detail.currentVersion() == null
            ? null
            : AgentFlowVersionResponse.from(detail.currentVersion(),
                readGraph(detail.currentVersion().graphJson()));
        return AgentFlowDetailResponse.from(detail, version);
    }

    public AgentFlowPageResponse page(PageResult<AgentFlow> result) {
        return AgentFlowPageResponse.from(result);
    }

    private JsonNode readGraph(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Grafo persistido inválido.");
        }
    }
}
