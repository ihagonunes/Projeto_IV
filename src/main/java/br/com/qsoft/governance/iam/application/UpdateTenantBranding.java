package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantNotFoundException;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de edição da identidade visual (white-label) do tenant (SPI-53).
 * <p>
 * A AUTORIZAÇÃO (permissão granular TENANT:MANAGE) é aplicada no endpoint pelo
 * {@code PermissionInterceptor} via {@code @RequiresPermission}. O tenant é
 * SEMPRE o do contexto autenticado — nunca um id vindo do cliente (ADR-03).
 * O formato das cores é validado no domínio ({@code Tenant.changeBranding}).
 */
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
        tenant.changeBranding(command.logoUrl(), command.primaryColor(), command.secondaryColor());
        return tenantRepository.save(tenant);
    }
}
