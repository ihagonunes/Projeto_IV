package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.LastTenantAdminException;
import br.com.qsoft.governance.iam.domain.exception.RoleAssignmentNotFoundException;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.iam.domain.rbac.SystemRole;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revogação lógica de uma concessão (ADR-37 item 6). Guardas:
 * <ul>
 *   <li>o ator não altera as próprias concessões (403);</li>
 *   <li>não se revoga o último {@code TENANT_ADMIN} ativo do tenant (409);</li>
 *   <li>concessão inexistente/de outro tenant/de outro usuário/já inativa → 404.</li>
 * </ul>
 * A autorização ({@code ROLE:MANAGE}, {@code TENANT_ONLY}) é aplicada no endpoint.
 */
@Service
public class RevokeRoleAssignment {

    private static final String TENANT_ADMIN_ROLE = "TENANT_ADMIN";

    private final RoleAssignmentRepository roleAssignmentRepository;
    private final RoleRepository roleRepository;

    public RevokeRoleAssignment(RoleAssignmentRepository roleAssignmentRepository,
                                RoleRepository roleRepository) {
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional
    public void revoke(RevokeRoleAssignmentCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        var tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        var actor = Objects.requireNonNull(command.actor(), "actor");
        var target = Objects.requireNonNull(command.targetUserId(), "targetUserId");
        var assignmentId = Objects.requireNonNull(command.assignmentId(), "assignmentId");

        RoleAssignment assignment = roleAssignmentRepository.findById(assignmentId)
            .filter(candidate -> candidate.tenantId().equals(tenantId)
                && candidate.userId().equals(target)
                && candidate.isActive())
            .orElseThrow(RoleAssignmentNotFoundException::new);

        Role role = roleRepository.findById(assignment.roleId())
            .orElseThrow(RoleAssignmentNotFoundException::new);

        // Guarda do último administrador (lockout) tem precedência: revogar o
        // último TENANT_ADMIN ativo (mesmo por si próprio) → 409.
        boolean tenantAdmin = role.systemRole()
            .filter(systemRole -> systemRole == SystemRole.TENANT_ADMIN)
            .isPresent();
        if (tenantAdmin) {
            Role tenantAdminRole = roleRepository.findByTenantIdAndName(tenantId, TENANT_ADMIN_ROLE)
                .orElseThrow(RoleAssignmentNotFoundException::new);
            long activeAdmins = roleAssignmentRepository
                .countActiveByTenantIdAndRoleId(tenantId, tenantAdminRole.id());
            if (activeAdmins <= 1) {
                throw new LastTenantAdminException();
            }
        }

        if (actor.equals(target)) {
            throw new AuthorizationDeniedException(
                "O ator não pode alterar as próprias concessões.");
        }

        roleAssignmentRepository.save(assignment.revoke(actor, Instant.now()));
    }
}
