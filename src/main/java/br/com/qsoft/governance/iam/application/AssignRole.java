package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.domain.rbac.GrantOrigin;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Atribuição direta de uma concessão de papel de sistema a um usuário existente
 * (ADR-37 item 6a). Nesta tarefa é um caso de uso interno (usado pelo fluxo de
 * provisionamento); as guardas de autorização e os endpoints entram na A6.
 * <p>
 * O escopo e o departamento são resolvidos por {@link RoleAssignment#grant}.
 */
@Service
public class AssignRole {

    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;

    public AssignRole(RoleRepository roleRepository,
                      RoleAssignmentRepository roleAssignmentRepository) {
        this.roleRepository = roleRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    public RoleAssignment assign(TenantId tenantId, UserId userId, String roleKey,
                                 UUID departmentId, GrantOrigin origin, UserId grantedBy) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
        Objects.requireNonNull(userId, "userId é obrigatório.");
        Objects.requireNonNull(roleKey, "roleKey é obrigatório.");
        Objects.requireNonNull(origin, "origin é obrigatório.");

        Role role = roleRepository.findByTenantIdAndName(tenantId, roleKey)
            .orElseThrow(() -> new IllegalArgumentException(
                "Papel de sistema inexistente: " + roleKey + "."));

        return roleAssignmentRepository.save(RoleAssignment.grant(
            userId, role, departmentId, origin, grantedBy, Instant.now()));
    }
}
