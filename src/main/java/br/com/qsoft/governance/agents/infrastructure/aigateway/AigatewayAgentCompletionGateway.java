package br.com.qsoft.governance.agents.infrastructure.aigateway;

import br.com.qsoft.governance.aigateway.api.completion.AICompletionService;
import br.com.qsoft.governance.aigateway.api.completion.CompletionCommand;
import br.com.qsoft.governance.aigateway.api.completion.CompletionMessage;
import br.com.qsoft.governance.aigateway.api.completion.CompletionResult;
import br.com.qsoft.governance.aigateway.api.exception.AIProviderFailureException;
import br.com.qsoft.governance.aigateway.api.exception.AIProviderTimeoutException;
import br.com.qsoft.governance.agents.domain.AgentCompletion;
import br.com.qsoft.governance.agents.domain.AgentCompletionGateway;
import br.com.qsoft.governance.agents.domain.AgentFlowFailureException;
import br.com.qsoft.governance.agents.domain.AgentFlowTimeoutException;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapta um nó `agent` ao `AICompletionService` (ADR-45): propósito
 * `AGENT_FLOW`, modelo resolvido pela política (nunca escolhido pelo cliente),
 * sem ferramentas no MVP. Nunca loga prompt ou resposta.
 */
public class AigatewayAgentCompletionGateway implements AgentCompletionGateway {

    private static final Logger log = LoggerFactory.getLogger(AigatewayAgentCompletionGateway.class);

    private final AICompletionService completionService;
    private final TenantId tenantId;
    private final UserId userId;

    public AigatewayAgentCompletionGateway(AICompletionService completionService,
                                           TenantId tenantId, UserId userId) {
        this.completionService = completionService;
        this.tenantId = tenantId;
        this.userId = userId;
    }

    @Override
    public AgentCompletion complete(String agentKey, String systemPrompt, String userContent) {
        TenantContext previous = TenantContextHolder.get();
        TenantContextHolder.set(new TenantContext(tenantId, userId));
        try {
            CompletionResult result = completionService.complete(new CompletionCommand(
                tenantId, userId, null, "AGENT_FLOW",
                List.of(new CompletionMessage("SYSTEM", systemPrompt, null),
                    new CompletionMessage("USER", userContent, null)),
                List.of()));
            return new AgentCompletion(result.requestId(), result.content());
        } catch (AIProviderTimeoutException ex) {
            throw new AgentFlowTimeoutException();
        } catch (AIProviderFailureException ex) {
            log.warn("Falha do provider na etapa de agente: {}", ex.getClass().getSimpleName());
            throw new AgentFlowFailureException("AGENT_COMPLETION_FAILED");
        } catch (RuntimeException ex) {
            // Só a classe do erro; nunca prompt, resposta ou credencial.
            log.warn("Falha na completion da etapa de agente: {}", ex.getClass().getSimpleName());
            throw new AgentFlowFailureException("AGENT_COMPLETION_FAILED");
        } finally {
            if (previous == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.set(previous);
            }
        }
    }
}
