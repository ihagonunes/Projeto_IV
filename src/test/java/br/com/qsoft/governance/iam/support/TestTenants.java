package br.com.qsoft.governance.iam.support;

import br.com.qsoft.governance.iam.application.AcceptInvite;
import br.com.qsoft.governance.iam.application.AcceptInviteCommand;
import br.com.qsoft.governance.iam.application.AssignRole;
import br.com.qsoft.governance.iam.application.CreateInvite;
import br.com.qsoft.governance.iam.application.CreateInviteCommand;
import br.com.qsoft.governance.iam.application.IssuedInvite;
import br.com.qsoft.governance.iam.application.RegisterTenant;
import br.com.qsoft.governance.iam.application.RegisterTenantCommand;
import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantDomainAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.TenantSlugAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.rbac.GrantOrigin;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Helper de ITs que prepara cenários <b>apenas pelos casos de uso de produção</b>
 * (ADR-37 item 8 / SPEC-01 §9):
 * <ul>
 *   <li>{@code RegisterTenant} cria o tenant e o admin {@code TENANT_ADMIN}
 *       (BOOTSTRAP);</li>
 *   <li>demais usuários entram pelo fluxo de convite atual
 *       ({@code CreateInvite} + {@code AcceptInvite}) e recebem a concessão por
 *       {@code AssignRole} — nunca por inserção manual em repositório.</li>
 * </ul>
 * Reside no pacote de testes; é registrado como bean no contexto de teste.
 */
@Component
public class TestTenants {

    private final RegisterTenant registerTenant;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final CreateInvite createInvite;
    private final AcceptInvite acceptInvite;
    private final AssignRole assignRole;

    public TestTenants(RegisterTenant registerTenant,
                       TenantRepository tenantRepository,
                       UserRepository userRepository,
                       RoleRepository roleRepository,
                       RoleAssignmentRepository roleAssignmentRepository,
                       CreateInvite createInvite,
                       AcceptInvite acceptInvite,
                       AssignRole assignRole) {
        this.registerTenant = registerTenant;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.createInvite = createInvite;
        this.acceptInvite = acceptInvite;
        this.assignRole = assignRole;
    }

    /** Registra (idempotentemente) um tenant com o admin via caso de uso de produção. */
    public Tenant ensureTenant(String slug, String name, String adminName,
                               String adminEmail, String adminPassword) {
        try {
            registerTenant.register(new RegisterTenantCommand(
                slug, name, slug + ".com", adminName, adminEmail, adminPassword));
        } catch (TenantSlugAlreadyExistsException | TenantDomainAlreadyExistsException ignored) {
            // já criado por um teste anterior
        }
        return tenantRepository.findBySlug(slug).orElseThrow();
    }

    /** Admin criado no bootstrap do tenant. */
    public User admin(Tenant tenant, String adminEmail) {
        return userRepository.findByTenantIdAndEmail(tenant.id(), adminEmail).orElseThrow();
    }

    /**
     * Garante um usuário não-admin (via convite + aceite) e a concessão pedida
     * (via {@code AssignRole}), de forma idempotente.
     */
    public User ensureMember(Tenant tenant, UserId inviterId, String email, String name,
                             String password, HierarchyLevel level, UUID departmentId,
                             String roleKey) {
        User member = userRepository.findByTenantIdAndEmail(tenant.id(), email).orElse(null);
        if (member == null) {
            IssuedInvite issued = null;
            try {
                issued = createInvite.create(new CreateInviteCommand(
                    tenant.id(), email, roleKey, departmentId, level, inviterId.value()));
            } catch (UserEmailAlreadyExistsException ignored) {
                // corrida: recriado por outro teste
            }
            member = issued != null
                ? acceptInvite.accept(new AcceptInviteCommand(issued.token(), name, password))
                : userRepository.findByTenantIdAndEmail(tenant.id(), email).orElseThrow();
        }
        assignIfAbsent(tenant, member, roleKey, departmentId, inviterId);
        return member;
    }

    /** Atribui a concessão pelo caso de uso de produção, se ainda não existir. */
    public void assignIfAbsent(Tenant tenant, User user, String roleKey,
                               UUID departmentId, UserId grantedBy) {
        Role role = roleRepository.findByTenantIdAndName(tenant.id(), roleKey).orElseThrow();
        boolean present = roleAssignmentRepository
            .findActiveByTenantIdAndUserId(tenant.id(), user.id())
            .stream()
            .anyMatch(grant -> grant.roleId().equals(role.id()));
        if (!present) {
            assignRole.assign(tenant.id(), user.id(), roleKey, departmentId,
                GrantOrigin.INVITE, grantedBy);
        }
    }
}
