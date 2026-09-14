package br.com.qsoft.governance.iam.infrastructure.bootstrap;

import br.com.qsoft.governance.iam.api.model.Permission;
import br.com.qsoft.governance.iam.application.ProvisionUser;
import br.com.qsoft.governance.iam.application.ProvisionUserCommand;
import br.com.qsoft.governance.iam.application.RegisterTenant;
import br.com.qsoft.governance.iam.application.RegisterTenantCommand;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantSlugAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Seeder de dados de DEMONSTRAÇÃO (apenas para a entrega/dev).
 * Cria, se ausente, o tenant "qsoft" e o usuário admin "admin@qsoft.com".
 * Desabilitado via app.bootstrap.demo-data=false.
 *
 * Credenciais DEMO (documentadas em docs/development/HOW_TO_RUN.md):
 *   tenant: qsoft | email: admin@qsoft.com | senha: Qsoft@2026
 *
 * Nota: reside no módulo IAM (e não em platform) para não criar o ciclo de
 * dependências iam <-> platform (ArchUnit); o JwtTokenIssuer já depende de platform.
 */
@Component
@ConditionalOnProperty(name = "app.bootstrap.demo-data", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    public static final String TENANT_SLUG = "qsoft";
    public static final String ADMIN_EMAIL = "admin@qsoft.com";
    public static final String ADMIN_PASSWORD = "Qsoft@2026";

    private final RegisterTenant registerTenant;
    private final TenantRepository tenantRepository;
    private final ProvisionUser provisionUser;

    public DemoDataSeeder(RegisterTenant registerTenant,
                          TenantRepository tenantRepository,
                          ProvisionUser provisionUser) {
        this.registerTenant = registerTenant;
        this.tenantRepository = tenantRepository;
        this.provisionUser = provisionUser;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            registerTenant.register(new RegisterTenantCommand(TENANT_SLUG, "QSOFT Demo"));
        } catch (TenantSlugAlreadyExistsException e) {
            log.info("Tenant demo '{}' já existe.", TENANT_SLUG);
        }

        Tenant tenant = tenantRepository.findBySlug(TENANT_SLUG).orElse(null);
        if (tenant == null) {
            log.warn("Não foi possível criar o tenant demo '{}'.", TENANT_SLUG);
            return;
        }

        try {
            provisionUser.provision(new ProvisionUserCommand(
                tenant.id(), ADMIN_EMAIL, "Administrador", HierarchyLevel.EXECUTIVE,
                ADMIN_PASSWORD, null, EnumSet.allOf(Permission.class)));
            log.info("Usuário demo criado: {} (ver senha em docs/development/HOW_TO_RUN.md).", ADMIN_EMAIL);
        } catch (UserEmailAlreadyExistsException e) {
            log.info("Usuário demo '{}' já existe.", ADMIN_EMAIL);
        }
    }
}
