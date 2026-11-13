package br.com.qsoft.governance.aigateway.api.completion;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompletionContractsTest {

    @Test
    void absentListsAndToolArgumentsHaveSafeDefaults() {
        var command = new CompletionCommand(TenantId.random(), UserId.random(), null, "CHAT", null, null);
        var response = new CompletionResult(UUID.randomUUID(), "Resposta", null, null,
            null, null, null, null, null, null, "INCONCLUSIVE", 0);

        assertThat(command.messages()).isEmpty();
        assertThat(command.tools()).isEmpty();
        assertThat(response.toolCalls()).isEmpty();
        assertThat(new ToolCall("call-1", "lookup", null).argumentsJson()).isEqualTo("{}");
    }

    @Test
    void systemMessageHasExpectedRoleAndCommandCopiesInputs() {
        CompletionMessage system = CompletionMessage.system("Instrução de teste");
        var messages = new ArrayList<>(List.of(system));
        var tool = new ToolSpec("lookup", null, "{}");
        var tools = new ArrayList<>(List.of(tool));
        var command = new CompletionCommand(TenantId.random(), UserId.random(), null, "CHAT", messages, tools);
        messages.clear();
        tools.clear();

        assertThat(system).isEqualTo(new CompletionMessage("SYSTEM", "Instrução de teste", null));
        assertThat(command.messages()).containsExactly(system);
        assertThat(command.tools()).containsExactly(tool);
        assertThat(new ToolCall("call-1", "lookup", "{\"id\":1}").argumentsJson()).isEqualTo("{\"id\":1}");
    }
}
