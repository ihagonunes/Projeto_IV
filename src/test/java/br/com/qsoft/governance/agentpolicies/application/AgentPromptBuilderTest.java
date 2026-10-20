package br.com.qsoft.governance.agentpolicies.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.aigateway.api.completion.CompletionMessage;
import br.com.qsoft.governance.aigateway.api.completion.ToolSpec;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Prompt fixo e trechos numerados na ordem do ranking (SPEC-05 §3.4/§6). */
class AgentPromptBuilderTest {

    private KnowledgeHit hit(String title, String text, int version) {
        return new KnowledgeHit(UUID.randomUUID(), text, UUID.randomUUID(), title, version, 1.0);
    }

    @Test
    void systemPromptEhFixoENaoRecebeConteudo() {
        assertThat(AgentPromptBuilder.SYSTEM_PROMPT)
            .contains("somente com base nos trechos numerados")
            .doesNotContain("Pergunta:");
    }

    @Test
    void montaTrechosNumeradosNaOrdemDoRankingEPergunta() {
        List<CompletionMessage> messages = AgentPromptBuilder.build(
            List.of(hit("Primeiro", "texto um", 1), hit("Segundo", "texto dois", 2)),
            "Posso viajar?");

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).role()).isEqualTo("SYSTEM");
        assertThat(messages.get(0).content()).isEqualTo(AgentPromptBuilder.SYSTEM_PROMPT);
        String user = messages.get(1).content();
        assertThat(user.indexOf("[1] (Primeiro, v1)"))
            .isLessThan(user.indexOf("[2] (Segundo, v2)"));
        assertThat(user).contains("texto um", "texto dois", "Pergunta: Posso viajar?");
    }

    @Test
    void ferramentasSaoAsDuasDaAllowlistComSchema() {
        List<ToolSpec> tools = AgentPromptBuilder.tools();

        assertThat(tools).extracting(ToolSpec::name)
            .containsExactly(AgentPromptBuilder.TOOL_SEARCH, AgentPromptBuilder.TOOL_MODEL);
        assertThat(tools).allSatisfy(tool ->
            assertThat(tool.jsonSchemaParameters()).contains("\"type\":\"object\""));
    }
}
