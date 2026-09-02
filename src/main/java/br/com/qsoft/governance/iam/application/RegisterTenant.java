package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantSlugAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de registro de organização (tenant) na plataforma.
 */
@Service
public class RegisterTenant {

    private final TenantRepository tenantRepository;

    public RegisterTenant(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
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

        Tenant tenant = Tenant.create(TenantId.random(), slug, name);
        return tenantRepository.save(tenant);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório.");
        }
        return value.trim();
    }
}
