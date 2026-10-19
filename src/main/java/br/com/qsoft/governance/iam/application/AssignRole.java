package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.application.rbac.GrantAuthority;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.RoleAssignmentAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.UserNotFoundException;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.iam.domain.rbac.SystemRole;
import br.com.qsoft.governance.sharedkernel.rbac.ScopeType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atribuição direta de uma concessão de papel de sistema a um usuário existente
 * (ADR-37 item 6a): exige {@code ROLE:MANAGE} (na prática, só {@code TENANT_ADMIN}),
 * o ator não pode ser o alvo e não se cria concessão ativa idêntica.
 */
@Service
public class AssignRole {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final GrantAuthority grantAuthority;

    public AssignRole(UserRepository userRepository,
                      RoleRepository roleRepository,
                      RoleAssignmentRepository roleAssignmentRepository,
                      GrantAuthority grantAuthority) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.grantAuthority = grantAuthority;
    }

    @Transactional
    public RoleAssignment assign(AssignRoleCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        var tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        var actor = Objects.requireNonNull(command.actor(), "actor");
        var target = Objects.requireNonNull(command.target(), "target");
        String roleKey = Objects.requireNonNull(command.roleKey(), "roleKey");
        var origin = Objects.requireNonNull(command.origin(), "origin");

        if (actor.equals(target)) {
            throw new AuthorizationDeniedException(
                "O ator não pode alterar as próprias concessões.");
        }

        SystemRole targetRole = SystemRole.fromKey(roleKey)
            .orElseThrow(() -> new IllegalArgumentException("roleKey inválido: " + roleKey + "."));
        UUID departmentId = command.departmentId();
        if (targetRole.scope() == ScopeType.DEPARTMENT && departmentId == null) {
            throw new IllegalArgumentException(
                "departmentId é obrigatório para papel departamental.");
        }
        if (!grantAuthority.canGrantDirect(tenantId, actor, targetRole, departmentId)) {
            throw new AuthorizationDeniedException(
                "Ator não pode conceder o papel " + roleKey + ".");
        }

        userRepository.findByTenantIdAndId(tenantId, target)
            .orElseThrow(() -> new UserNotFoundException(target));

        Role role = roleRepository.findByTenantIdAndName(tenantId, roleKey)
            .orElseThrow(() -> new IllegalStateException("Papel de sistema ausente: " + roleKey + "."));
        UUID scopedDepartment = targetRole.scope() == ScopeType.DEPARTMENT ? departmentId : null;

        boolean duplicate = roleAssignmentRepository
            .findActiveByTenantIdAndUserId(tenantId, target)
            .stream()
            .anyMatch(assignment -> assignment.roleId().equals(role.id())
                && Objects.equals(assignment.departmentId(), scopedDepartment));
        if (duplicate) {
            throw new RoleAssignmentAlreadyExistsException();
        }

        return roleAssignmentRepository.save(RoleAssignment.grant(
            target, role, departmentId, origin, actor, Instant.now()));
    }
}
