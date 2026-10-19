package br.com.qsoft.governance.iam.application.rbac;

import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.rbac.Permission;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.iam.domain.rbac.SystemRole;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Regra de delegação de concessões (ADR-37 item 4 / SPEC-01 §4):
 * <ul>
 *   <li>{@code TENANT_ADMIN} ativo concede qualquer papel;</li>
 *   <li>um não-admin só concede papéis delegáveis ({@link SystemRole#isDelegableByNonAdmins()}),
 *       com posto maior, permissão adequada ({@code INVITE:MANAGE} para convite,
 *       {@code ROLE:MANAGE} para atribuição direta) e escopo que contenha o alvo.</li>
 * </ul>
 * Reutilizado por A5, A6 e A7.
 */
@Service
public class GrantAuthority {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;

    public GrantAuthority(UserRepository userRepository,
                          RoleRepository roleRepository,
                          RoleAssignmentRepository roleAssignmentRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    /** Delegação por convite: exige {@code INVITE:MANAGE}. */
    public boolean canGrantByInvite(TenantId tenantId, UserId actor, SystemRole target,
                                    UUID departmentId) {
        return canGrant(tenantId, actor, target, departmentId,
            Permission.of(Resource.INVITE, Action.MANAGE));
    }

    /** Atribuição direta: exige {@code ROLE:MANAGE}. */
    public boolean canGrantDirect(TenantId tenantId, UserId actor, SystemRole target,
                                  UUID departmentId) {
        return canGrant(tenantId, actor, target, departmentId,
            Permission.of(Resource.ROLE, Action.MANAGE));
    }

    private boolean canGrant(TenantId tenantId, UserId actor, SystemRole target,
                             UUID departmentId, Permission required) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
        Objects.requireNonNull(actor, "actor é obrigatório.");
        Objects.requireNonNull(target, "target é obrigatório.");

        User user = userRepository.findByTenantIdAndId(tenantId, actor).orElse(null);
        if (user == null || user.status() != UserStatus.ACTIVE || !user.tenantId().equals(tenantId)) {
            return false;
        }

        var grants = roleAssignmentRepository.findActiveByTenantIdAndUserId(tenantId, actor);

        // TENANT_ADMIN concede qualquer papel.
        for (RoleAssignment grant : grants) {
            Role role = roleRepository.findById(grant.roleId()).orElse(null);
            if (role != null && role.tenantId().equals(tenantId)
                && role.systemRole().filter(systemRole -> systemRole == SystemRole.TENANT_ADMIN).isPresent()) {
                return true;
            }
        }

        if (!target.isDelegableByNonAdmins()) {
            return false;
        }

        for (RoleAssignment grant : grants) {
            Role role = roleRepository.findById(grant.roleId()).orElse(null);
            if (role == null || !role.tenantId().equals(tenantId)) {
                continue;
            }
            SystemRole actorRole = role.systemRole().orElse(null);
            if (actorRole == null || actorRole.rank() <= target.rank()) {
                continue;
            }
            if (!role.hasPermission(required)) {
                continue;
            }
            if (scopeContains(grant, target, departmentId)) {
                return true;
            }
        }
        return false;
    }

    private boolean scopeContains(RoleAssignment grant, SystemRole target, UUID departmentId) {
        return ScopeCoverage.contains(grant, target, departmentId);
    }
}
