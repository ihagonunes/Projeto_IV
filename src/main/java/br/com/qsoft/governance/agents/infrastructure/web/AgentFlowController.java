package br.com.qsoft.governance.agents.infrastructure.web;

import br.com.qsoft.governance.agents.application.AgentFlowDetail;
import br.com.qsoft.governance.agents.application.CreateAgentFlow;
import br.com.qsoft.governance.agents.application.CreateAgentFlowCommand;
import br.com.qsoft.governance.agents.application.DeleteAgentFlow;
import br.com.qsoft.governance.agents.application.GetAgentFlow;
import br.com.qsoft.governance.agents.application.ListAgentFlows;
import br.com.qsoft.governance.agents.application.PublishAgentFlow;
import br.com.qsoft.governance.agents.application.SaveAgentFlowVersion;
import br.com.qsoft.governance.agents.application.SaveAgentFlowVersionCommand;
import br.com.qsoft.governance.agents.application.ValidateAgentFlowGraph;
import br.com.qsoft.governance.agents.domain.AgentFlowStatus;
import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.iam.api.rbac.Targeting;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ciclo de vida persistente do fluxo (ADR-44 / SPI-180): criar, listar,
 * carregar, salvar nova versão e arquivar. Dono/tenant vêm do contexto.
 */
@RestController
@RequestMapping("/api/v1/agentes/fluxos")
public class AgentFlowController {

    private final ListAgentFlows listAgentFlows;
    private final CreateAgentFlow createAgentFlow;
    private final GetAgentFlow getAgentFlow;
    private final SaveAgentFlowVersion saveAgentFlowVersion;
    private final DeleteAgentFlow deleteAgentFlow;
    private final ValidateAgentFlowGraph validateAgentFlowGraph;
    private final PublishAgentFlow publishAgentFlow;
    private final AgentFlowResponseMapper mapper;

    public AgentFlowController(ListAgentFlows listAgentFlows, CreateAgentFlow createAgentFlow,
                               GetAgentFlow getAgentFlow,
                               SaveAgentFlowVersion saveAgentFlowVersion,
                               DeleteAgentFlow deleteAgentFlow,
                               ValidateAgentFlowGraph validateAgentFlowGraph,
                               PublishAgentFlow publishAgentFlow,
                               AgentFlowResponseMapper mapper) {
        this.listAgentFlows = listAgentFlows;
        this.createAgentFlow = createAgentFlow;
        this.getAgentFlow = getAgentFlow;
        this.saveAgentFlowVersion = saveAgentFlowVersion;
        this.deleteAgentFlow = deleteAgentFlow;
        this.validateAgentFlowGraph = validateAgentFlowGraph;
        this.publishAgentFlow = publishAgentFlow;
        this.mapper = mapper;
    }

    @GetMapping
    @RequiresPermission(resource = Resource.AGENT, action = Action.READ,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<AgentFlowPageResponse> list(
            @RequestParam(required = false) AgentFlowStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.ok(mapper.page(
            listAgentFlows.list(context.tenantId(), context.userId(), status, page, size)));
    }

    @PostMapping
    @RequiresPermission(resource = Resource.AGENT, action = Action.MANAGE,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<AgentFlowDetailResponse> create(
            @Valid @RequestBody CreateAgentFlowRequest request) {
        TenantContext context = TenantContextHolder.require();
        AgentFlowDetail detail = createAgentFlow.create(new CreateAgentFlowCommand(
            context.tenantId(), context.userId(), request.name(), request.departmentId(),
            request.schemaVersion(), mapper.writeGraph(request.graphJson())));
        return ResponseEntity.status(HttpStatus.CREATED)
            .eTag(etag(detail.flow().rowVersion()))
            .body(mapper.detail(detail));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = Resource.AGENT, action = Action.READ,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<AgentFlowDetailResponse> get(@PathVariable UUID id) {
        TenantContext context = TenantContextHolder.require();
        AgentFlowDetail detail = getAgentFlow.get(context.tenantId(), context.userId(), id);
        return ResponseEntity.ok()
            .eTag(etag(detail.flow().rowVersion()))
            .body(mapper.detail(detail));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = Resource.AGENT, action = Action.MANAGE,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<AgentFlowDetailResponse> save(
            @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody SaveAgentFlowVersionRequest request) {
        TenantContext context = TenantContextHolder.require();
        Long expected = expectedRowVersion(ifMatch, request.rowVersion());
        AgentFlowDetail detail = saveAgentFlowVersion.save(new SaveAgentFlowVersionCommand(
            context.tenantId(), context.userId(), id, expected, request.name(),
            request.schemaVersion(), mapper.writeGraph(request.graphJson())));
        return ResponseEntity.ok()
            .eTag(etag(detail.flow().rowVersion()))
            .body(mapper.detail(detail));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = Resource.AGENT, action = Action.MANAGE,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        TenantContext context = TenantContextHolder.require();
        deleteAgentFlow.delete(context.tenantId(), context.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/validar")
    @RequiresPermission(resource = Resource.AGENT, action = Action.MANAGE,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<FlowValidationResponse> validate(
            @PathVariable UUID id,
            @Valid @RequestBody ValidateAgentFlowRequest request) {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.ok(FlowValidationResponse.from(
            validateAgentFlowGraph.validateForFlow(context.tenantId(), context.userId(), id,
                request.schemaVersion(), mapper.writeGraph(request.graphJson()))));
    }

    @PostMapping("/{id}/publicar")
    @RequiresPermission(resource = Resource.AGENT, action = Action.MANAGE,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<AgentFlowDetailResponse> publish(
            @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody(required = false) PublishAgentFlowRequest request) {
        TenantContext context = TenantContextHolder.require();
        Long expected = expectedRowVersion(ifMatch, request == null ? null : request.rowVersion());
        AgentFlowDetail detail = publishAgentFlow.publish(
            context.tenantId(), context.userId(), id, expected);
        return ResponseEntity.ok()
            .eTag(etag(detail.flow().rowVersion()))
            .body(mapper.detail(detail));
    }

    private static Long expectedRowVersion(String ifMatch, Long bodyRowVersion) {
        if (ifMatch != null && !ifMatch.isBlank()) {
            return parseIfMatch(ifMatch);
        }
        return bodyRowVersion;
    }

    /** Aceita `1`, `"1"` e `W/"1"`; qualquer outra coisa é 400. */
    private static long parseIfMatch(String raw) {
        String value = raw.trim();
        if (value.startsWith("W/")) {
            value = value.substring(2).trim();
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.isBlank() || value.contains(",")) {
            throw new IllegalArgumentException("If-Match inválido.");
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 1) {
                throw new IllegalArgumentException("If-Match inválido.");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("If-Match inválido.");
        }
    }

    private static String etag(long rowVersion) {
        return "\"" + rowVersion + "\"";
    }
}
