package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.TenantDomainRepository;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.rbac.ProvisionDefaultRoles;
import br.com.qsoft.governance.iam.domain.exception.TenantDomainAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.TenantSlugAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso de registro de organização (tenant) na plataforma.
 * <p>
 * Na MESMA transação (ADR-37 item 1): cria o tenant, provisiona os papéis de
 * sistema e cria o primeiro administrador com concessão {@code TENANT_ADMIN}
 * ({@code origin = BOOTSTRAP}). Qualquer falha reverte tudo.
 * <p>
 * SPI-54: o domínio de e-mail corporativo é obrigatório e único globalmente —
 * impedir a sobreposição é o que mantém a resolução do tenant pelo domínio do
 * e-mail determinística (ADR-32).
 */
@Service
public class RegisterTenant {

    private final TenantRepository tenantRepository;
    private final TenantDomainRepository tenantDomainRepository;
    private final ProvisionDefaultRoles provisionDefaultRoles;
    private final BootstrapTenantAdmin bootstrapTenantAdmin;

    public RegisterTenant(TenantRepository tenantRepository,
                          TenantDomainRepository tenantDomainRepository,
                          ProvisionDefaultRoles provisionDefaultRoles,
                          BootstrapTenantAdmin bootstrapTenantAdmin) {
        this.tenantRepository = tenantRepository;
        this.tenantDomainRepository = tenantDomainRepository;
        this.provisionDefaultRoles = provisionDefaultRoles;
        this.bootstrapTenantAdmin = bootstrapTenantAdmin;
    }

    @Transactional
    public Tenant register(RegisterTenantCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        String slug = requireNonBlank(command.slug(), "slug");
        String name = requireNonBlank(command.name(), "name");
        String emailDomain = Tenant.normalizeDomain(command.emailDomain());
        if (emailDomain == null || emailDomain.isBlank()) {
            throw new IllegalArgumentException("emailDomain é obrigatório.");
        }
        String adminName = requireNonBlank(command.adminName(), "adminName");
        String adminEmail = requireNonBlank(command.adminEmail(), "adminEmail");
        String adminPassword = requireNonBlank(command.adminPassword(), "adminPassword");
        if (adminPassword.length() < 8) {
            throw new IllegalArgumentException("adminPassword deve ter ao menos 8 caracteres.");
        }
        if (!emailDomain.equalsIgnoreCase(domainOf(adminEmail))) {
            throw new IllegalArgumentException("adminEmail deve pertencer a emailDomain.");
        }

        // Unicidade do slug é um invariante de domínio (deve ser único globalmente).
        if (tenantRepository.existsBySlug(slug)) {
            throw new TenantSlugAlreadyExistsException(slug);
        }

        // Unicidade do domínio corporativo (SPI-54): impede a sobreposição de
        // domínios e mantém a resolução do tenant no login determinística.
        if (tenantDomainRepository.existsByEmailDomain(emailDomain)) {
            throw new TenantDomainAlreadyExistsException(emailDomain);
        }

        Tenant tenant = tenantRepository.save(
            Tenant.create(TenantId.random(), slug, name, emailDomain));

        // Persiste os papéis de sistema do tenant (SPI-43/ADR-36).
        provisionDefaultRoles.provision(tenant.id());

        // Cria o primeiro administrador + concessão TENANT_ADMIN (ADR-37).
        bootstrapTenantAdmin.bootstrap(tenant.id(), adminName, adminEmail, adminPassword);

        return tenant;
    }

    private static String domainOf(String email) {
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) {
            throw new IllegalArgumentException("adminEmail inválido.");
        }
        String domain = Tenant.normalizeDomain(email.substring(at + 1));
        if (domain == null || domain.isBlank()) {
            throw new IllegalArgumentException("adminEmail inválido.");
        }
        return domain;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório.");
        }
        return value.trim();
    }
}
