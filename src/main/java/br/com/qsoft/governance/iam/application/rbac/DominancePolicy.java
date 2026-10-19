package br.com.qsoft.governance.iam.application.rbac;

import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.iam.domain.rbac.SystemRole;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Dominância (SPEC-01 §4): quem pode editar, desativar, revogar ou mover outro
 * usuário.
 * <ul>
 *   <li>ator == alvo → nunca domina;</li>
 *   <li>TENANT_ADMIN domina qualquer usuário do tenant;</li>
 *   <li>os demais precisam dominar TODAS as concessões ativas do alvo (posto maior e
 *       escopo que contenha papel/departamento) e o alvo precisa estar lotado num
 *       departamento coberto pelo ator.</li>
 * </ul>
 */
@Service
public class DominancePolicy {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;

    public DominancePolicy(UserRepository userRepository,
                           RoleRepository roleRepository,
                           RoleAssignmentRepository roleAssignmentRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    public boolean dominates(TenantId tenantId, UserId actor, UserId target) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
        Objects.requireNonNull(actor, "actor é obrigatório.");
        Objects.requireNonNull(target, "target é obrigatório.");
        if (actor.equals(target)) {
            return false;
        }

        User actorUser = userRepository.findByTenantIdAndId(tenantId, actor).orElse(null);
        User targetUser = userRepository.findByTenantIdAndId(tenantId, target).orElse(null);
        if (actorUser == null || actorUser.status() != UserStatus.ACTIVE || targetUser == null) {
            return false;
        }

        List<GrantedRole> actorGrants = grants(tenantId, actor);
        if (actorGrants.stream().anyMatch(grant -> grant.systemRole() == SystemRole.TENANT_ADMIN)) {
            return true;
        }

        for (GrantedRole targetGrant : grants(tenantId, target)) {
            // Concessões SELF não guardam departamento; nesse caso vale a lotação do alvo.
            UUID targetDepartment = targetGrant.assignment().departmentId() != null
                ? targetGrant.assignment().departmentId()
                : targetUser.departmentId();
            boolean dominated = actorGrants.stream().anyMatch(actorGrant ->
                actorGrant.systemRole().rank() > targetGrant.systemRole().rank()
                    && ScopeCoverage.contains(actorGrant.assignment(), targetGrant.systemRole(),
                        targetDepartment));
            if (!dominated) {
                return false;
            }
        }

        return actorGrants.stream().anyMatch(grant ->
            ScopeCoverage.coversDepartment(grant.assignment(), targetUser.departmentId()));
    }

    /** O ator tem concessão que cobre o departamento (TENANT cobre todos)? */
    public boolean coversDepartment(TenantId tenantId, UserId actor, UUID departmentId) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
        Objects.requireNonNull(actor, "actor é obrigatório.");
        if (departmentId == null) {
            return false;
        }
        return grants(tenantId, actor).stream().anyMatch(grant ->
            grant.systemRole() == SystemRole.TENANT_ADMIN
                || ScopeCoverage.coversDepartment(grant.assignment(), departmentId));
    }

    private List<GrantedRole> grants(TenantId tenantId, UserId userId) {
        List<GrantedRole> result = new ArrayList<>();
        for (RoleAssignment assignment :
            roleAssignmentRepository.findActiveByTenantIdAndUserId(tenantId, userId)) {
            Role role = roleRepository.findById(assignment.roleId()).orElse(null);
            if (role == null || !role.tenantId().equals(tenantId)) {
                continue;
            }
            role.systemRole().ifPresent(systemRole ->
                result.add(new GrantedRole(assignment, systemRole)));
        }
        return result;
    }

    private record GrantedRole(RoleAssignment assignment, SystemRole systemRole) {
    }
}
