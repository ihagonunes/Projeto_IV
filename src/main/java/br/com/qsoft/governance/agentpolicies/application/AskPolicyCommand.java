package br.com.qsoft.governance.agentpolicies.application;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.UUID;

/** Pergunta ao agente de políticas; o tenant/ator vêm do contexto autenticado. */
public record AskPolicyCommand(
    TenantId tenantId,
    UserId userId,
    String question,
    UUID requestedModelId
) {
}
