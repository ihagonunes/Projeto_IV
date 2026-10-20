package br.com.qsoft.governance.agentpolicies.infrastructure.web;

import br.com.qsoft.governance.agentpolicies.application.AskPolicyAgent;
import br.com.qsoft.governance.agentpolicies.application.AskPolicyCommand;
import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.iam.api.rbac.Targeting;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint do agente de políticas (SPEC-05 §4). Gate
 * {@code KNOWLEDGE_DOCUMENT:READ} em {@code BY_TARGET}; a recuperação é o
 * filtro fino (tenant + departamento).
 */
@RestController
@RequestMapping("/api/v1/agente-politicas")
public class PolicyAgentController {

    private final AskPolicyAgent agent;

    public PolicyAgentController(AskPolicyAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/perguntas")
    @RequiresPermission(resource = Resource.KNOWLEDGE_DOCUMENT, action = Action.READ,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<PolicyAnswerResponse> ask(
            @Valid @RequestBody PolicyQuestionRequest request) {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.ok(PolicyAnswerResponse.from(agent.ask(
            new AskPolicyCommand(context.tenantId(), context.userId(), request.pergunta(),
                request.requestedModelId()))));
    }
}
