package br.com.qsoft.governance.agents.application.port;

import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.agents.domain.AgentFlowStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência de fluxos (ADR-44). Toda leitura filtra {@code tenant_id}; um
 * fluxo de outro tenant é indistinguível de inexistente (404 na API).
 */
public interface AgentFlowRepository {

    /** Insert/update guardado pelo tenant da linha existente. */
    AgentFlow save(AgentFlow flow);

    Optional<AgentFlow> findById(TenantId tenantId, UUID flowId);

    List<AgentFlow> findByOwner(TenantId tenantId, UserId ownerUserId);

    List<AgentFlow> findByDepartment(TenantId tenantId, UUID departmentId);

    /** Página visível ao escopo (SELF/DEPARTMENT/TENANT), sem deletados. */
    PageResult<AgentFlow> findVisible(TenantId tenantId, VisibilityScope scope,
                                      AgentFlowStatus status, int page, int size);

    /** Update condicional por `row_version`; `false` = conflito/perda de corrida. */
    boolean updateIfRowVersion(AgentFlow flow, long expectedRowVersion);

    boolean markDeleted(TenantId tenantId, UUID flowId, Instant deletedAt);
}
