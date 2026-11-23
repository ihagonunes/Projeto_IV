package br.com.qsoft.governance.agents.infrastructure.aigateway;

import br.com.qsoft.governance.aigateway.api.completion.AICompletionService;
import br.com.qsoft.governance.agents.application.port.AgentCompletionGatewayFactory;
import br.com.qsoft.governance.agents.domain.AgentCompletionGateway;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import org.springframework.stereotype.Component;

/** Factory de produção do gateway de completion de nós `agent`. */
@Component
public class AigatewayAgentCompletionGatewayFactory implements AgentCompletionGatewayFactory {

    private final AICompletionService completionService;

    public AigatewayAgentCompletionGatewayFactory(AICompletionService completionService) {
        this.completionService = completionService;
    }

    @Override
    public AgentCompletionGateway forExecution(TenantId tenantId, UserId userId) {
        return new AigatewayAgentCompletionGateway(completionService, tenantId, userId);
    }
}
