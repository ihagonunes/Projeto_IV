package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantNotFoundException;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ChangeTenantStatus {

    private final TenantRepository tenantRepository;

    public ChangeTenantStatus(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public Tenant changeStatus(ChangeTenantStatusCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        TenantStatus newStatus = Objects.requireNonNull(command.newStatus(), "newStatus");

        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId));

        if (newStatus == TenantStatus.ACTIVE) {
            tenant.activate();
        } else {
            tenant.deactivate();
        }

        return tenantRepository.save(tenant);
    }
}
