package br.com.qsoft.governance.agents.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentPolicyJsonTest {

    private final AgentPolicyJson policyJson = new AgentPolicyJson(new ObjectMapper());

    @Test
    void acceptsTheClosedMvpVocabulary() {
        assertThatCode(() -> policyJson.validateTools("[]")).doesNotThrowAnyException();
        assertThatCode(() -> policyJson.validateTools("[\"consultar-politicas\"]"))
            .doesNotThrowAnyException();
        assertThatCode(() -> policyJson.validateKnowledgeScope("{\"mode\":\"NONE\"}"))
            .doesNotThrowAnyException();
        assertThatCode(() -> policyJson.validateModelPolicy("{\"mode\":\"INHERIT\"}"))
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsToolsThatAreNotTextArrays() {
        assertThatThrownBy(() -> policyJson.validateTools("{}"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policyJson.validateTools("[1]"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policyJson.validateTools("[null]"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policyJson.validateTools("não é json"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void knowledgeScopeOnlyAcceptsNone() {
        assertThatThrownBy(() -> policyJson.validateKnowledgeScope("{\"mode\":\"TENANT\"}"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
            policyJson.validateKnowledgeScope("{\"mode\":\"NONE\",\"topics\":[\"x\"]}"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policyJson.validateKnowledgeScope("[]"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policyJson.validateKnowledgeScope("{\"mode\":\"none\"}"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void modelPolicyOnlyAcceptsInherit() {
        assertThatThrownBy(() -> policyJson.validateModelPolicy("{\"mode\":\"EXPLICIT\"}"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policyJson.validateModelPolicy("{\"mode\":\"INHERIT\",\"x\":1}"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policyJson.validateModelPolicy("null"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
