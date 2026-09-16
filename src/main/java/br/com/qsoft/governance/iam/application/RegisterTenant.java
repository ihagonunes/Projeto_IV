package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.rbac.ProvisionDefaultRoles;
import br.com.qsoft.governance.iam.domain.exception.TenantSlugAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de registro de organização (tenant) na plataforma.
 * Ao criar o tenant, provisiona a matriz de permissões padrão (SPI-43).
 */
@Service
public class RegisterTenant {

    private final TenantRepository tenantRepository;
    private final ProvisionDefaultRoles provisionDefaultRoles;

    public RegisterTenant(TenantRepository tenantRepository,
                          ProvisionDefaultRoles provisionDefaultRoles) {
        this.tenantRepository = tenantRepository;
        this.provisionDefaultRoles = provisionDefaultRoles;
    }

    public Tenant register(RegisterTenantCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        String slug = requireNonBlank(command.slug(), "slug");
        String name = requireNonBlank(command.name(), "name");

        // Unicidade do slug é um invariante de domínio (deve ser único globalmente).
        if (tenantRepository.existsBySlug(slug)) {
            throw new TenantSlugAlreadyExistsException(slug);
        }

        Tenant tenant = tenantRepository.save(Tenant.create(TenantId.random(), slug, name));

        // Persiste a matriz de permissões padrão do tenant (SPI-43).
        provisionDefaultRoles.provision(tenant.id());

        return tenant;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório.");
        }
        return value.trim();
    }
}
