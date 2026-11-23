package br.com.qsoft.governance.agents.infrastructure.web;

import br.com.qsoft.governance.agents.application.AgentCatalogEntry;
import br.com.qsoft.governance.agents.application.DeleteAgentOverride;
import br.com.qsoft.governance.agents.application.QueryAgentCatalog;
import br.com.qsoft.governance.agents.application.UpsertAgentOverride;
import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.iam.api.rbac.Targeting;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo global de agentes e override por tenant (ADR-44 / SPI-180).
 * O catálogo global nunca é editado em runtime: só existe leitura e override
 * do tenant autenticado.
 */
@RestController
@RequestMapping("/api/v1/agentes/catalogo")
public class AgentCatalogController {

    private final QueryAgentCatalog queryAgentCatalog;
    private final UpsertAgentOverride upsertAgentOverride;
    private final DeleteAgentOverride deleteAgentOverride;

    public AgentCatalogController(QueryAgentCatalog queryAgentCatalog,
                                  UpsertAgentOverride upsertAgentOverride,
                                  DeleteAgentOverride deleteAgentOverride) {
        this.queryAgentCatalog = queryAgentCatalog;
        this.upsertAgentOverride = upsertAgentOverride;
        this.deleteAgentOverride = deleteAgentOverride;
    }

    @GetMapping
    @RequiresPermission(resource = Resource.AGENT, action = Action.READ,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<AgentCatalogResponse> catalog() {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.ok(AgentCatalogResponse.from(
            queryAgentCatalog.query(context.tenantId())));
    }

    @PutMapping("/{agentKey}/override")
    @RequiresPermission(resource = Resource.AGENT, action = Action.MANAGE,
        targeting = Targeting.TENANT_ONLY)
    public ResponseEntity<AgentCatalogEntryResponse> upsertOverride(
            @PathVariable String agentKey,
            @Valid @RequestBody AgentOverrideRequest request) {
        TenantContext context = TenantContextHolder.require();
        AgentCatalogEntry entry = upsertAgentOverride.upsert(
            request.toCommand(context.tenantId(), agentKey));
        return ResponseEntity.ok(AgentCatalogEntryResponse.from(entry));
    }

    @DeleteMapping("/{agentKey}/override")
    @RequiresPermission(resource = Resource.AGENT, action = Action.MANAGE,
        targeting = Targeting.TENANT_ONLY)
    public ResponseEntity<Void> deleteOverride(@PathVariable String agentKey) {
        TenantContext context = TenantContextHolder.require();
        deleteAgentOverride.delete(context.tenantId(), agentKey);
        return ResponseEntity.noContent().build();
    }
}
