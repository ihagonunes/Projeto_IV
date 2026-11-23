package br.com.qsoft.governance.agents.application.port;

import br.com.qsoft.governance.agents.domain.AgentCompletionGateway;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;

/** Cria a porta de completion vinculada ao tenant/usuário da execução. */
public interface AgentCompletionGatewayFactory {

    AgentCompletionGateway forExecution(TenantId tenantId, UserId userId);
}
