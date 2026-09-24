package br.com.qsoft.governance.iam.infrastructure.bootstrap;

import br.com.qsoft.governance.iam.api.model.Permission;
import br.com.qsoft.governance.iam.application.ProvisionUser;
import br.com.qsoft.governance.iam.application.ProvisionUserCommand;
import br.com.qsoft.governance.iam.application.RegisterTenant;
import br.com.qsoft.governance.iam.application.RegisterTenantCommand;
import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantDomainAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.TenantSlugAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import java.util.EnumSet;
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
 * Cria, se ausente, o tenant "qsoft" e o usuário admin "admin@qsoft.com",
 * atribuindo ao admin o papel padrão EXECUTIVE (modelo RBAC granular).
 *
 * Nota: reside no módulo IAM (e não em platform) para não criar o ciclo de
 * dependências iam <-> platform (ArchUnit).
 */
@Component
@ConditionalOnProperty(name = "app.bootstrap.demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    public static final String TENANT_SLUG = "qsoft";
    public static final String ADMIN_EMAIL = "admin@qsoft.com";
    public static final String EMAIL_DOMAIN = "qsoft.com";

    private final RegisterTenant registerTenant;
    private final TenantRepository tenantRepository;
    private final ProvisionUser provisionUser;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final String demoPassword;

    public DemoDataSeeder(RegisterTenant registerTenant,
                          TenantRepository tenantRepository,
                          ProvisionUser provisionUser,
                          UserRepository userRepository,
                          RoleRepository roleRepository,
                          RoleAssignmentRepository roleAssignmentRepository,
                          @Value("${app.bootstrap.demo-password:}") String demoPassword) {
        this.registerTenant = registerTenant;
        this.tenantRepository = tenantRepository;
        this.provisionUser = provisionUser;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
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
            registerTenant.register(new RegisterTenantCommand(TENANT_SLUG, "QSOFT Demo", EMAIL_DOMAIN));
        } catch (TenantSlugAlreadyExistsException | TenantDomainAlreadyExistsException e) {
            log.info("Tenant demo '{}' já existe (ou o domínio '{}' já está em uso).",
                TENANT_SLUG, EMAIL_DOMAIN);
        }

        Tenant tenant = tenantRepository.findBySlug(TENANT_SLUG).orElse(null);
        if (tenant == null) {
            log.warn("Não foi possível criar o tenant demo '{}'.", TENANT_SLUG);
            return;
        }

        User admin = provisionAdmin(tenant);
        assignExecutiveRole(tenant, admin);
    }

    private User provisionAdmin(Tenant tenant) {
        try {
            User created = provisionUser.provision(new ProvisionUserCommand(
                tenant.id(), ADMIN_EMAIL, "Administrador", HierarchyLevel.EXECUTIVE,
                demoPassword, null, EnumSet.allOf(Permission.class)));
            log.info("Usuário demo criado: {}.", ADMIN_EMAIL);
            return created;
        } catch (UserEmailAlreadyExistsException e) {
            log.info("Usuário demo '{}' já existe.", ADMIN_EMAIL);
            return userRepository.findByTenantIdAndEmail(tenant.id(), ADMIN_EMAIL).orElse(null);
        }
    }

    private void assignExecutiveRole(Tenant tenant, User admin) {
        if (admin == null) {
            return;
        }
        Role executive = roleRepository
            .findByTenantIdAndName(tenant.id(), HierarchyLevel.EXECUTIVE.name())
            .orElse(null);
        if (executive == null) {
            log.warn("Papel padrão EXECUTIVE não encontrado para o tenant demo.");
            return;
        }
        boolean alreadyAssigned = roleAssignmentRepository
            .findByTenantIdAndUserId(tenant.id(), admin.id())
            .stream()
            .anyMatch(assignment -> assignment.roleId().equals(executive.id()));
        if (!alreadyAssigned) {
            roleAssignmentRepository.save(
                RoleAssignment.create(admin.id(), executive.id(), tenant.id(), null));
            log.info("Papel EXECUTIVE atribuído ao admin demo.");
        }
    }
}
