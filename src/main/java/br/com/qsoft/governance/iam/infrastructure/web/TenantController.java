package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.api.model.AuthorizationResult;
import br.com.qsoft.governance.iam.api.model.Permission;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.application.ChangeTenantStatus;
import br.com.qsoft.governance.iam.application.ChangeTenantStatusCommand;
import br.com.qsoft.governance.iam.application.RegisterTenant;
import br.com.qsoft.governance.iam.application.RegisterTenantCommand;
import br.com.qsoft.governance.iam.application.UpdateTenantBranding;
import br.com.qsoft.governance.iam.application.UpdateTenantBrandingCommand;
import br.com.qsoft.governance.iam.application.UpdateTenantData;
import br.com.qsoft.governance.iam.application.UpdateTenantDataCommand;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de gestão do tenant (organização).
 *
 * <ul>
 *   <li>POST  /api/v1/tenants          — onboarding público (sem autenticação)</li>
 *   <li>PATCH /api/v1/tenants/me       — atualiza dados cadastrais (SPI-52)</li>
 *   <li>PATCH /api/v1/tenants/me/branding — atualiza identidade visual (SPI-53)</li>
 *   <li>PATCH /api/v1/tenants/me/status   — altera status ativo/inativo (SPI-57)</li>
 * </ul>
 *
 * O tenant vem sempre do contexto autenticado (TenantContextHolder).
 * Nunca é aceito pelo corpo da requisição.
 */
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final RegisterTenant registerTenant;
    private final UpdateTenantData updateTenantData;
    private final UpdateTenantBranding updateTenantBranding;
    private final ChangeTenantStatus changeTenantStatus;
    private final AuthorizationQuery authorizationQuery;

    public TenantController(RegisterTenant registerTenant,
                            UpdateTenantData updateTenantData,
                            UpdateTenantBranding updateTenantBranding,
                            ChangeTenantStatus changeTenantStatus,
                            AuthorizationQuery authorizationQuery) {
        this.registerTenant = registerTenant;
        this.updateTenantData = updateTenantData;
        this.updateTenantBranding = updateTenantBranding;
        this.changeTenantStatus = changeTenantStatus;
        this.authorizationQuery = authorizationQuery;
    }

    /**
     * Onboarding público — cria o tenant antes de qualquer autenticação.
     * Não requer JWT.
     */
    @PostMapping
    public ResponseEntity<TenantResponse> create(@Valid @RequestBody TenantRequest request) {
        Tenant tenant = registerTenant.register(
            new RegisterTenantCommand(request.slug(), request.name()));
        return ResponseEntity.status(HttpStatus.CREATED).body(TenantResponse.from(tenant));
    }

    /**
     * Atualiza dados cadastrais do tenant autenticado (SPI-52).
     * Requer MANAGE_TENANT.
     */
    @PatchMapping("/me")
    public ResponseEntity<TenantResponse> updateData(
            @Valid @RequestBody UpdateTenantDataRequest request) {
        TenantContext context = TenantContextHolder.require();
        requirePermission(context, Permission.MANAGE_TENANT, "atualizar dados do tenant");

        Tenant tenant = updateTenantData.updateData(
            new UpdateTenantDataCommand(context.tenantId(), request.name()));
        return ResponseEntity.ok(TenantResponse.from(tenant));
    }

    /**
     * Atualiza identidade visual / white-label do tenant autenticado (SPI-53).
     * Requer MANAGE_TENANT.
     */
    @PatchMapping("/me/branding")
    public ResponseEntity<TenantResponse> updateBranding(
            @Valid @RequestBody UpdateTenantBrandingRequest request) {
        TenantContext context = TenantContextHolder.require();
        requirePermission(context, Permission.MANAGE_TENANT, "atualizar branding do tenant");

        Tenant tenant = updateTenantBranding.updateBranding(
            new UpdateTenantBrandingCommand(
                context.tenantId(),
                request.logoUrl(),
                request.primaryColor(),
                request.secondaryColor()));
        return ResponseEntity.ok(TenantResponse.from(tenant));
    }

    /**
     * Altera status ativo/inativo do tenant autenticado (SPI-57).
     * Requer MANAGE_TENANT.
     */
    @PatchMapping("/me/status")
    public ResponseEntity<TenantResponse> changeStatus(
            @Valid @RequestBody ChangeTenantStatusRequest request) {
        TenantContext context = TenantContextHolder.require();
        requirePermission(context, Permission.MANAGE_TENANT, "alterar status do tenant");

        Tenant tenant = changeTenantStatus.changeStatus(
            new ChangeTenantStatusCommand(context.tenantId(), request.status()));
        return ResponseEntity.ok(TenantResponse.from(tenant));
    }

    private void requirePermission(TenantContext context, Permission permission, String action) {
        AuthorizationResult authorization =
            authorizationQuery.checkPermission(context.userId(), permission);
        if (!authorization.allowed()) {
            throw new AuthorizationDeniedException(
                authorization.denialReason() != null
                    ? authorization.denialReason()
                    : "Usuário não autorizado a " + action + ".");
        }
    }
}
