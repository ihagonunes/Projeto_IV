package br.com.qsoft.governance.agents.infrastructure.web;

import br.com.qsoft.governance.agents.application.ExecuteAgentFlow;
import br.com.qsoft.governance.agents.application.GetAgentFlowExecution;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Execução determinística de uma versão publicada (ADR-45). Síncrona, com
 * `Idempotency-Key`; o resultado e os recibos não expõem prompt.
 */
@RestController
@RequestMapping("/api/v1/agentes")
public class AgentExecutionController {

    private final ExecuteAgentFlow executeAgentFlow;
    private final GetAgentFlowExecution getAgentFlowExecution;

    public AgentExecutionController(ExecuteAgentFlow executeAgentFlow,
                                    GetAgentFlowExecution getAgentFlowExecution) {
        this.executeAgentFlow = executeAgentFlow;
        this.getAgentFlowExecution = getAgentFlowExecution;
    }

    @PostMapping("/fluxos/{id}/execucoes")
    @RequiresPermission(resource = Resource.AGENT, action = Action.RUN,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<AgentExecutionResponse> execute(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ExecuteAgentFlowRequest request) {
        TenantContext context = TenantContextHolder.require();
        var view = executeAgentFlow.execute(context.tenantId(), context.userId(), id,
            idempotencyKey, request.input());
        return ResponseEntity.status(HttpStatus.CREATED).body(AgentExecutionResponse.from(view));
    }

    @GetMapping("/execucoes/{id}")
    @RequiresPermission(resource = Resource.AGENT, action = Action.READ,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<AgentExecutionResponse> get(@PathVariable UUID id) {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.ok(AgentExecutionResponse.from(
            getAgentFlowExecution.get(context.tenantId(), context.userId(), id)));
    }
}
