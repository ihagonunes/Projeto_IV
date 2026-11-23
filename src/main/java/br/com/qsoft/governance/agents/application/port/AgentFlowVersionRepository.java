package br.com.qsoft.governance.agents.application.port;

import br.com.qsoft.governance.agents.domain.AgentFlowVersion;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Versões imutáveis do grafo (ADR-44). A tabela não tem {@code tenant_id}: a
 * isolação vem do vínculo com {@code agents_flows} e é aplicada explicitamente
 * em toda leitura e escrita.
 */
public interface AgentFlowVersionRepository {

    /** Insere somente se o fluxo pertence ao tenant informado. */
    AgentFlowVersion save(TenantId tenantId, AgentFlowVersion version);

    List<AgentFlowVersion> findByFlow(TenantId tenantId, UUID flowId);

    Optional<AgentFlowVersion> find(TenantId tenantId, UUID flowId, int versionNumber);

    Optional<AgentFlowVersion> findById(TenantId tenantId, UUID flowId, UUID versionId);

    /** Próximo `version_number` do fluxo (1 quando não há versões). */
    int nextVersionNumber(TenantId tenantId, UUID flowId);
}
