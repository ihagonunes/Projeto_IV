package br.com.qsoft.governance.agents.infrastructure.aigateway;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.aigateway.api.completion.AICompletionService;
import br.com.qsoft.governance.aigateway.api.exception.AIProviderFailureException;
import br.com.qsoft.governance.aigateway.api.exception.AIProviderTimeoutException;
import br.com.qsoft.governance.agents.domain.AgentFlowFailureException;
import br.com.qsoft.governance.agents.domain.AgentFlowTimeoutException;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import org.junit.jupiter.api.Test;

class AigatewayAgentCompletionGatewayTest {

    private AigatewayAgentCompletionGateway gateway(AICompletionService service) {
        return new AigatewayAgentCompletionGateway(service, TenantId.random(), UserId.random());
    }

    @Test
    void mapsProviderTimeoutToDomainTimeout() {
        AICompletionService service = command -> {
            throw new AIProviderTimeoutException();
        };

        assertThatThrownBy(() -> gateway(service).complete("policies", "S", "U"))
            .isInstanceOf(AgentFlowTimeoutException.class);
    }

    @Test
    void mapsProviderFailureToStableCode() {
        AICompletionService service = command -> {
            throw new AIProviderFailureException("HTTP_500");
        };

        assertThatThrownBy(() -> gateway(service).complete("policies", "S", "U"))
            .isInstanceOf(AgentFlowFailureException.class)
            .hasMessage("AGENT_COMPLETION_FAILED");
    }
}
