package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantNotFoundException;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class UpdateTenantData {

    private final TenantRepository tenantRepository;

    public UpdateTenantData(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public Tenant updateData(UpdateTenantDataCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");

        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId));

        tenant.updateName(command.name());
        return tenantRepository.save(tenant);
    }
}
