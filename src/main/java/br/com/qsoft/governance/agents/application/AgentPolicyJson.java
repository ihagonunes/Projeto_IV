package br.com.qsoft.governance.agents.application;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Vocabulário fechado das políticas JSON do catálogo (ADR-44).
 * <p>
 * MVP sem RAG: {@code knowledgeScopeJson} aceita somente {@code {"mode":"NONE"}}
 * — o agente trabalha apenas com o material da tarefa. {@code modelPolicyJson}
 * aceita somente {@code {"mode":"INHERIT"}}. {@code toolsJson} é um array de
 * identificadores textuais (vazio = nenhuma ferramenta).
 * <p>
 * Novos modos exigem mudança explícita de contrato (e migration de dados).
 */
@Component
public class AgentPolicyJson {

    private static final Set<String> KNOWLEDGE_KEYS = Set.of("mode");
    private static final Set<String> MODEL_KEYS = Set.of("mode");
    private static final String KNOWLEDGE_NONE = "NONE";
    private static final String MODEL_INHERIT = "INHERIT";

    private final ObjectMapper objectMapper;

    public AgentPolicyJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validateTools(String raw) {
        JsonNode node = parse(raw, "toolsJson");
        if (!node.isArray()) {
            throw invalid("toolsJson", "deve ser um array de identificadores.");
        }
        for (JsonNode item : node.values()) {
            if (!item.isString() || item.asString().isBlank()) {
                throw invalid("toolsJson", "aceita somente textos não vazios.");
            }
        }
    }

    public void validateKnowledgeScope(String raw) {
        JsonNode node = parse(raw, "knowledgeScopeJson");
        requireClosedObject(node, "knowledgeScopeJson", KNOWLEDGE_KEYS);
        if (!KNOWLEDGE_NONE.equals(node.path("mode").asString(""))) {
            throw invalid("knowledgeScopeJson",
                "aceita somente {\"mode\":\"NONE\"} neste incremento.");
        }
    }

    public void validateModelPolicy(String raw) {
        JsonNode node = parse(raw, "modelPolicyJson");
        requireClosedObject(node, "modelPolicyJson", MODEL_KEYS);
        if (!MODEL_INHERIT.equals(node.path("mode").asString(""))) {
            throw invalid("modelPolicyJson",
                "aceita somente {\"mode\":\"INHERIT\"} neste incremento.");
        }
    }

    private static void requireClosedObject(JsonNode node, String field, Set<String> allowedKeys) {
        if (!node.isObject()) {
            throw invalid(field, "deve ser um objeto.");
        }
        Set<String> keys = node.properties().stream()
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
        if (!keys.equals(allowedKeys)) {
            throw invalid(field, "aceita somente " + allowedKeys + ".");
        }
    }

    private JsonNode parse(String raw, String field) {
        try {
            return objectMapper.readTree(raw);
        } catch (JacksonException ex) {
            throw invalid(field, "não é JSON válido.");
        }
    }

    private static IllegalArgumentException invalid(String field, String message) {
        return new IllegalArgumentException(field + " " + message);
    }
}
