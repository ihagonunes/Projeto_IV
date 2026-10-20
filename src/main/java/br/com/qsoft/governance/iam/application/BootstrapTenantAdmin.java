package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.domain.rbac.GrantOrigin;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Bootstrap do primeiro administrador do tenant (ADR-37 item 1): cria o usuário
 * (senha BCrypt) e a concessão {@code TENANT_ADMIN}/TENANT com origem
 * {@code BOOTSTRAP} e {@code grantedBy = null}.
 * <p>
 * Executa dentro da transação de {@link RegisterTenant}: qualquer falha reverte
 * o tenant, os papéis e o usuário. A senha nunca é logada nem devolvida.
 */
@Service
public class BootstrapTenantAdmin {

    private static final String TENANT_ADMIN_ROLE = "TENANT_ADMIN";

    private final ProvisionUser provisionUser;
    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;

    public BootstrapTenantAdmin(ProvisionUser provisionUser,
                                RoleRepository roleRepository,
                                RoleAssignmentRepository roleAssignmentRepository) {
        this.provisionUser = provisionUser;
        this.roleRepository = roleRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    public User bootstrap(TenantId tenantId, String adminName, String adminEmail, String adminPassword) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");

        User admin = provisionUser.provision(new ProvisionUserCommand(
            tenantId, adminEmail, adminName, HierarchyLevel.EXECUTIVE, adminPassword, null));

        Role tenantAdmin = roleRepository.findByTenantIdAndName(tenantId, TENANT_ADMIN_ROLE)
            .orElseThrow(() -> new IllegalStateException(
                "Papel de sistema " + TENANT_ADMIN_ROLE + " ausente no tenant."));

        roleAssignmentRepository.save(RoleAssignment.grant(
            admin.id(), tenantAdmin, null, GrantOrigin.BOOTSTRAP, null, Instant.now()));

        return admin;
    }
}
