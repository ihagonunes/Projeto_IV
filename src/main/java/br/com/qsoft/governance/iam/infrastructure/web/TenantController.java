package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.application.RegisterTenant;
import br.com.qsoft.governance.iam.application.RegisterTenantCommand;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de cadastro de organização (tenant).
 * Em onboarding, o tenant ainda não existe, portanto este endpoint é público.
 */
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final RegisterTenant registerTenant;

    public TenantController(RegisterTenant registerTenant) {
        this.registerTenant = registerTenant;
    }

    @PostMapping
    public ResponseEntity<TenantResponse> create(@Valid @RequestBody TenantRequest request) {
        Tenant tenant = registerTenant.register(new RegisterTenantCommand(request.slug(), request.name()));
        return ResponseEntity.status(HttpStatus.CREATED).body(TenantResponse.from(tenant));
    }
}
