package br.com.qsoft.governance.iam.application.rbac;

import br.com.qsoft.governance.iam.api.model.AuthorizationResult;
import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.rbac.Permission;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Policy evaluator: motor centralizado de avaliação de permissão.
 *
 * ALLOW quando: Tenant ACTIVE AND User ACTIVE AND sameTenant AND existe uma
 * atribuição (policy) de papel, aplicável ao contexto, cujo papel contém a
 * permissão (resource × action).
 *
 * Escopo de Department: uma atribuição com {@code departmentId} definido só se
 * aplica quando o {@code departmentId} do contexto é igual; uma atribuição
 * sem departamento (tenant-wide) aplica-se sempre.
 */
@Service
public class PolicyEvaluator {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;

    public PolicyEvaluator(UserRepository userRepository,
                           TenantRepository tenantRepository,
                           RoleRepository roleRepository,
                           RoleAssignmentRepository roleAssignmentRepository) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.roleRepository = roleRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    public AuthorizationResult evaluate(EvaluationContext context, Permission permission) {
        Objects.requireNonNull(context, "context é obrigatório.");
        Objects.requireNonNull(permission, "permission é obrigatória.");

        User user = userRepository.findById(context.userId()).orElse(null);
        if (user == null || user.status() != UserStatus.ACTIVE) {
            return AuthorizationResult.deny("Usuário inexistente ou inativo.");
        }
        if (!user.tenantId().equals(context.tenantId())) {
            return AuthorizationResult.deny("Usuário não pertence ao tenant do contexto.");
        }

        var tenant = tenantRepository.findById(context.tenantId()).orElse(null);
        if (tenant == null || tenant.status() != TenantStatus.ACTIVE) {
            return AuthorizationResult.deny("Tenant inexistente ou inativo.");
        }

        List<RoleAssignment> assignments =
            roleAssignmentRepository.findByTenantIdAndUserId(context.tenantId(), context.userId());

        for (RoleAssignment assignment : assignments) {
            if (assignment.isDepartmentScoped()
                && !Objects.equals(assignment.departmentId(), context.departmentId())) {
                continue;
            }
            Role role = roleRepository.findById(assignment.roleId()).orElse(null);
            if (role != null && role.hasPermission(permission)) {
                return AuthorizationResult.allow();
            }
        }

        return AuthorizationResult.deny(
            "Permissão negada: " + permission.resource() + ":" + permission.action() + ".");
    }

    public boolean isAllowed(EvaluationContext context, Permission permission) {
        return evaluate(context, permission).allowed();
    }
}
