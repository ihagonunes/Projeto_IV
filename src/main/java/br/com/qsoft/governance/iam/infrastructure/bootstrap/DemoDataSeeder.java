package br.com.qsoft.governance.iam.infrastructure.bootstrap;

import br.com.qsoft.governance.iam.application.RegisterTenant;
import br.com.qsoft.governance.iam.application.RegisterTenantCommand;
import br.com.qsoft.governance.iam.domain.exception.TenantDomainAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.TenantSlugAlreadyExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Seeder de dados de DEMONSTRAÇÃO — DESABILITADO por padrão.
 * Só executa quando {@code app.bootstrap.demo-data=true} E
 * {@code app.bootstrap.demo-password} está definido (sem senha hardcoded).
 * <p>
 * Usa o mesmo caso de uso de produção do onboarding (ADR-37 item 8):
 * {@link RegisterTenant} cria o tenant, os papéis de sistema e o admin com
 * concessão {@code TENANT_ADMIN}/BOOTSTRAP. Idempotente por slug/domínio.
 */
@Component
@ConditionalOnProperty(name = "app.bootstrap.demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    public static final String TENANT_SLUG = "qsoft";
    public static final String ADMIN_EMAIL = "admin@qsoft.com";
    public static final String EMAIL_DOMAIN = "qsoft.com";

    private final RegisterTenant registerTenant;
    private final String demoPassword;

    public DemoDataSeeder(RegisterTenant registerTenant,
                          @Value("${app.bootstrap.demo-password:}") String demoPassword) {
        this.registerTenant = registerTenant;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (demoPassword == null || demoPassword.isBlank()) {
            log.warn("Seed demo habilitado, mas 'app.bootstrap.demo-password' não está definido; "
                + "nenhum dado de demonstração será criado.");
            return;
        }

        try {
            registerTenant.register(new RegisterTenantCommand(
                TENANT_SLUG, "QSOFT Demo", EMAIL_DOMAIN, "Administrador", ADMIN_EMAIL, demoPassword));
            log.info("Tenant demo '{}' e admin criados.", TENANT_SLUG);
        } catch (TenantSlugAlreadyExistsException | TenantDomainAlreadyExistsException e) {
            log.info("Tenant demo '{}' já existe (ou o domínio '{}' já está em uso).",
                TENANT_SLUG, EMAIL_DOMAIN);
        }
    }
}
