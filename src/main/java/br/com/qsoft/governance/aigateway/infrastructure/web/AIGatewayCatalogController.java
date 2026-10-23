package br.com.qsoft.governance.aigateway.infrastructure.web;

import br.com.qsoft.governance.aigateway.application.ChangeModelStatus;
import br.com.qsoft.governance.aigateway.application.ChangeModelStatusCommand;
import br.com.qsoft.governance.aigateway.application.CreateModel;
import br.com.qsoft.governance.aigateway.application.CreateModelCommand;
import br.com.qsoft.governance.aigateway.application.CreateProvider;
import br.com.qsoft.governance.aigateway.application.CreateProviderCommand;
import br.com.qsoft.governance.aigateway.application.ListModels;
import br.com.qsoft.governance.aigateway.application.ListProviders;
import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.iam.api.rbac.Targeting;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administração do catálogo técnico (Provider/Model) do tenant autenticado
 * (SPI-81). Autorização administrativa via IAM (`AI_TOOL:MANAGE`, ADR-34).
 */
@RestController
@RequestMapping("/api/v1/ai-gateway")
public class AIGatewayCatalogController {

    public record DashboardModelResponse(UUID id, String name) { }

    @GetMapping("/models/dashboard")
    @RequiresPermission(resource = Resource.USAGE, action = Action.READ,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<List<DashboardModelResponse>> dashboardModels() {
        return ResponseEntity.ok(listModels.availableForDashboard(TenantContextHolder.require().tenantId())
            .stream().map(model -> new DashboardModelResponse(model.id(), model.name())).toList());
    }

    private final CreateProvider createProvider;
    private final ListProviders listProviders;
    private final CreateModel createModel;
    private final ListModels listModels;
    private final ChangeModelStatus changeModelStatus;

    public AIGatewayCatalogController(CreateProvider createProvider,
                                      ListProviders listProviders,
                                      CreateModel createModel,
                                      ListModels listModels,
                                      ChangeModelStatus changeModelStatus) {
        this.createProvider = createProvider;
        this.listProviders = listProviders;
        this.createModel = createModel;
        this.listModels = listModels;
        this.changeModelStatus = changeModelStatus;
    }

    @GetMapping("/providers")
    @RequiresPermission(resource = Resource.AI_TOOL, action = Action.MANAGE)
    public ResponseEntity<List<ProviderResponse>> listProviders() {
        TenantContext context = TenantContextHolder.require();
        List<ProviderResponse> providers = listProviders.list(context.tenantId()).stream()
            .map(ProviderResponse::from)
            .toList();
        return ResponseEntity.ok(providers);
    }

    @PostMapping("/providers")
    @RequiresPermission(resource = Resource.AI_TOOL, action = Action.MANAGE)
    public ResponseEntity<ProviderResponse> createProvider(@Valid @RequestBody ProviderRequest request) {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED).body(ProviderResponse.from(
            createProvider.create(new CreateProviderCommand(
                context.tenantId(), request.name(), request.kind(),
                request.baseUrl(), request.credentialRef(), request.timeoutMs()))));
    }

    @GetMapping("/models")
    @RequiresPermission(resource = Resource.AI_TOOL, action = Action.MANAGE)
    public ResponseEntity<List<ModelResponse>> listModels() {
        TenantContext context = TenantContextHolder.require();
        List<ModelResponse> models = listModels.list(context.tenantId()).stream()
            .map(ModelResponse::from)
            .toList();
        return ResponseEntity.ok(models);
    }

    @PostMapping("/models")
    @RequiresPermission(resource = Resource.AI_TOOL, action = Action.MANAGE)
    public ResponseEntity<ModelResponse> createModel(@Valid @RequestBody ModelRequest request) {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED).body(ModelResponse.from(
            createModel.create(new CreateModelCommand(
                context.tenantId(), request.providerId(), request.name(), request.externalName(),
                request.inputPricePer1k(), request.outputPricePer1k()))));
    }

    @PatchMapping("/models/{id}/status")
    @RequiresPermission(resource = Resource.AI_TOOL, action = Action.MANAGE)
    public ResponseEntity<ModelResponse> changeModelStatus(@PathVariable UUID id,
                                                           @Valid @RequestBody ModelStatusRequest request) {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.ok(ModelResponse.from(changeModelStatus.change(
            new ChangeModelStatusCommand(context.tenantId(), id, request.status()))));
    }
}
