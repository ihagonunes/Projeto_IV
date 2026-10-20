package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.rbac.PolicyEvaluator;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Monta a visão de acesso do usuário (SPEC-01 §7.3): concessões ATIVAS com papel
 * e escopo + união das permissões. Somente para exibição (menus); a autorização
 * real é feita pelo {@link PolicyEvaluator} por requisição.
 */
@Service
public class GetUserAccess {

    private final RoleAssignmentRepository roleAssignmentRepository;
    private final RoleRepository roleRepository;
    private final PolicyEvaluator policyEvaluator;

    public GetUserAccess(RoleAssignmentRepository roleAssignmentRepository,
                         RoleRepository roleRepository,
                         PolicyEvaluator policyEvaluator) {
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.roleRepository = roleRepository;
        this.policyEvaluator = policyEvaluator;
    }

    public UserAccessView forUser(TenantId tenantId, UserId userId) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
        Objects.requireNonNull(userId, "userId é obrigatório.");

        List<GrantView> concessions = new ArrayList<>();
        for (RoleAssignment assignment :
            roleAssignmentRepository.findActiveByTenantIdAndUserId(tenantId, userId)) {
            Role role = roleRepository.findById(assignment.roleId()).orElse(null);
            if (role == null || !role.tenantId().equals(tenantId)) {
                continue;
            }
            concessions.add(new GrantView(
                assignment.id().value(),
                role.name(),
                assignment.scopeType().name(),
                assignment.departmentId(),
                assignment.origin().name()));
        }

        return new UserAccessView(
            userId.value(), concessions, policyEvaluator.effectivePermissions(tenantId, userId));
    }
}
