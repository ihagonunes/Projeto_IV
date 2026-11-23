package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.UUID;

/**
 * Criação de fluxo: dono e tenant vêm do contexto autenticado, nunca do corpo.
 * `departmentId` nulo usa a lotação do ator.
 */
public record CreateAgentFlowCommand(
    TenantId tenantId,
    UserId ownerUserId,
    String name,
    UUID departmentId,
    int schemaVersion,
    String graphJson
) {
}
