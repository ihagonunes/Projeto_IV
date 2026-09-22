package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantNotFoundException;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de edição dos dados cadastrais do tenant (SPI-52).
 * <p>
 * A AUTORIZAÇÃO (permissão granular TENANT:MANAGE) é aplicada no endpoint pelo
 * {@code PermissionInterceptor} via {@code @RequiresPermission}. O tenant é
 * SEMPRE o do contexto autenticado — nunca um id vindo do cliente (ADR-03).
 * O slug é imutável (identifica a organização na resolução do tenant).
 */
@Service
public class UpdateTenant {

    private final TenantRepository tenantRepository;

    public UpdateTenant(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public Tenant update(UpdateTenantCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        if (command.name() == null || command.name().isBlank()) {
            throw new IllegalArgumentException("name é obrigatório.");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId));
        tenant.changeName(command.name());
        return tenantRepository.save(tenant);
    }
}
