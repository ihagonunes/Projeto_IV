package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.iam.application.GetCurrentTenant;
import br.com.qsoft.governance.iam.application.RegisterTenant;
import br.com.qsoft.governance.iam.application.RegisterTenantCommand;
import br.com.qsoft.governance.iam.application.UpdateTenant;
import br.com.qsoft.governance.iam.application.UpdateTenantBranding;
import br.com.qsoft.governance.iam.application.UpdateTenantBrandingCommand;
import br.com.qsoft.governance.iam.application.UpdateTenantCommand;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de organização (tenant).
 * <ul>
 *   <li>{@code POST /api/v1/tenants}: cadastro (onboarding — o tenant ainda não
 *       existe, portanto é público).</li>
 *   <li>{@code GET /api/v1/tenants/me}: dados cadastrais do tenant do contexto
 *       autenticado (SPI-52).</li>
 *   <li>{@code PUT /api/v1/tenants/me}: edição dos dados cadastrais
 *       (SPI-52, requer TENANT:MANAGE).</li>
 * </ul>
 * O tenant vem SEMPRE do contexto autenticado — nunca do cliente (ADR-03).
 */
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final RegisterTenant registerTenant;
    private final GetCurrentTenant getCurrentTenant;
    private final UpdateTenant updateTenant;
    private final UpdateTenantBranding updateTenantBranding;

    public TenantController(RegisterTenant registerTenant,
                            GetCurrentTenant getCurrentTenant,
                            UpdateTenant updateTenant,
                            UpdateTenantBranding updateTenantBranding) {
        this.registerTenant = registerTenant;
        this.getCurrentTenant = getCurrentTenant;
        this.updateTenant = updateTenant;
        this.updateTenantBranding = updateTenantBranding;
    }

    @PostMapping
    public ResponseEntity<TenantResponse> create(@Valid @RequestBody TenantRequest request) {
        Tenant tenant = registerTenant.register(new RegisterTenantCommand(
            request.slug(), request.name(), request.emailDomain()));
        return ResponseEntity.status(HttpStatus.CREATED).body(TenantResponse.from(tenant));
    }

    /** Dados cadastrais do tenant do contexto autenticado (SPI-52). */
    @GetMapping("/me")
    public ResponseEntity<TenantResponse> current() {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.ok(TenantResponse.from(getCurrentTenant.current(context.tenantId())));
    }

    /** Edita os dados cadastrais do tenant do contexto autenticado (SPI-52). */
    @PutMapping("/me")
    @RequiresPermission(resource = Resource.TENANT, action = Action.MANAGE)
    public ResponseEntity<TenantResponse> update(@Valid @RequestBody TenantUpdateRequest request) {
        TenantContext context = TenantContextHolder.require();
        Tenant tenant = updateTenant.update(new UpdateTenantCommand(context.tenantId(), request.name()));
        return ResponseEntity.ok(TenantResponse.from(tenant));
    }

    /** Identidade visual (white-label) do tenant do contexto autenticado (SPI-53). */
    @GetMapping("/me/identidade-visual")
    public ResponseEntity<TenantBrandingResponse> currentBranding() {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.ok(
            TenantBrandingResponse.from(getCurrentTenant.current(context.tenantId())));
    }

    /** Atualiza a identidade visual do tenant do contexto autenticado (SPI-53). */
    @PutMapping("/me/identidade-visual")
    @RequiresPermission(resource = Resource.TENANT, action = Action.MANAGE)
    public ResponseEntity<TenantBrandingResponse> updateBranding(
            @Valid @RequestBody TenantBrandingRequest request) {
        TenantContext context = TenantContextHolder.require();
        Tenant tenant = updateTenantBranding.updateBranding(new UpdateTenantBrandingCommand(
            context.tenantId(), request.logoUrl(), request.primaryColor(), request.secondaryColor()));
        return ResponseEntity.ok(TenantBrandingResponse.from(tenant));
    }
}
