package br.com.qsoft.governance.agents.application.port;

import br.com.qsoft.governance.agents.domain.AgentDefinitionOverride;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.List;
import java.util.Optional;

/**
 * Overrides do catálogo por tenant (ADR-44). Toda operação é tenant-scoped: o
 * {@code tenantId} vem do contexto autenticado e filtra explicitamente.
 */
public interface AgentCatalogOverrideRepository {

    List<AgentDefinitionOverride> findByTenant(TenantId tenantId);

    Optional<AgentDefinitionOverride> find(TenantId tenantId, String agentKey, int version);

    /** Upsert restrito ao tenant; nunca altera a linha de outro tenant. */
    void save(AgentDefinitionOverride override);

    boolean delete(TenantId tenantId, String agentKey, int version);

    /** Remove todas as versões do override do agente no tenant; devolve quantas linhas saíram. */
    int deleteByTenantAndAgentKey(TenantId tenantId, String agentKey);
}
