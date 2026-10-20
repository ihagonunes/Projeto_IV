package br.com.qsoft.governance.aigateway.api.query;

import br.com.qsoft.governance.aigateway.api.model.ResolvedModelInfo;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Optional;
import java.util.UUID;

/**
 * Consulta pública do modelo aplicável a um usuário (ADR-08/ADR-38). Usada por
 * ferramentas do agente sem expor o roteamento interno do aigateway.
 */
public interface ModelResolutionQuery {

    Optional<ResolvedModelInfo> resolve(TenantId tenantId, UserId userId, UUID requestedModelId);
}
