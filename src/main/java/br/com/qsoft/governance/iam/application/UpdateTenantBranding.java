package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantNotFoundException;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class UpdateTenantBranding {

    private final TenantRepository tenantRepository;

    public UpdateTenantBranding(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public Tenant updateBranding(UpdateTenantBrandingCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");

        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId));

        tenant.updateBranding(command.logoUrl(), command.primaryColor(), command.secondaryColor());
        return tenantRepository.save(tenant);
    }
}
