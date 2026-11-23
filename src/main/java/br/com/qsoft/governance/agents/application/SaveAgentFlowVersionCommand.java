package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.UUID;

/**
 * Nova versão de um fluxo existente. `expectedRowVersion` é obrigatório
 * (If-Match/row_version) e divergência vira 412.
 */
public record SaveAgentFlowVersionCommand(
    TenantId tenantId,
    UserId actorUserId,
    UUID flowId,
    Long expectedRowVersion,
    String name,
    int schemaVersion,
    String graphJson
) {
}
