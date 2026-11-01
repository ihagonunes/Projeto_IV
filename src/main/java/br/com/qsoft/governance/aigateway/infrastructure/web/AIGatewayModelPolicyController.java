package br.com.qsoft.governance.aigateway.infrastructure.web;

import br.com.qsoft.governance.aigateway.application.ConfigureDepartmentPolicy;
import br.com.qsoft.governance.aigateway.application.ReadDepartmentModelPolicy;
import br.com.qsoft.governance.aigateway.application.ResolveModelForUser;
import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuração da ModelPolicy por departamento (SPI-82) e consulta do modelo
 * aplicável ao usuário autenticado (SPI-84).
 */
@RestController
@RequestMapping("/api/v1/ai-gateway")
public class AIGatewayModelPolicyController {

    private final ConfigureDepartmentPolicy configureDepartmentPolicy;
    private final ReadDepartmentModelPolicy readDepartmentModelPolicy;
    private final ResolveModelForUser resolveModelForUser;

    public AIGatewayModelPolicyController(ConfigureDepartmentPolicy configureDepartmentPolicy,
                                          ReadDepartmentModelPolicy readDepartmentModelPolicy,
                                          ResolveModelForUser resolveModelForUser) {
        this.configureDepartmentPolicy = configureDepartmentPolicy;
        this.readDepartmentModelPolicy = readDepartmentModelPolicy;
        this.resolveModelForUser = resolveModelForUser;
    }

    /** Lê a política do departamento; sem política devolve o formato vazio (SPI-87). */
    @GetMapping("/departments/{id}/model-policy")
    @RequiresPermission(resource = Resource.AI_TOOL, action = Action.MANAGE)
    public ResponseEntity<DepartmentModelPolicyResponse> policy(@PathVariable UUID id) {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.ok(DepartmentModelPolicyResponse.from(
            readDepartmentModelPolicy.read(context.tenantId(), id)));
    }

    /** Configura a ModelPolicy do departamento. Requer AI_TOOL:MANAGE (SPI-82). */
    @PutMapping("/departments/{id}/model-policy")
    @RequiresPermission(resource = Resource.AI_TOOL, action = Action.MANAGE)
    public ResponseEntity<Void> configure(@PathVariable UUID id,
                                          @Valid @RequestBody ModelPolicyRequest request) {
        TenantContext context = TenantContextHolder.require();
        configureDepartmentPolicy.configure(
            context.tenantId(), id, request.allowedModelIds(), request.defaultModelId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Modelo aplicável ao usuário autenticado (SPI-84). Autenticado — o próprio
     * usuário consulta o modelo que pode usar, conforme o seu departamento.
     */
    @GetMapping("/models/permitido")
    public ResponseEntity<ResolvedModelResponse> allowedModel(
            @RequestParam(required = false) UUID requestedModelId) {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.ok(ResolvedModelResponse.from(
            resolveModelForUser.resolve(context.tenantId(), context.userId(), requestedModelId)));
    }
}
