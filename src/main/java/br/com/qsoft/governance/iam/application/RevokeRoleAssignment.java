package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.rbac.TenantAdminGuard;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.RoleAssignmentNotFoundException;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revogação lógica de uma concessão (ADR-37 item 6). Guardas:
 * <ul>
 *   <li>não se revoga o último {@code TENANT_ADMIN} ativo do tenant (409, com
 *       lock pessimista — ver {@link TenantAdminGuard});</li>
 *   <li>o ator não altera as próprias concessões (403);</li>
 *   <li>concessão inexistente/de outro tenant/de outro usuário/já inativa → 404.</li>
 * </ul>
 * A autorização ({@code ROLE:MANAGE}, {@code TENANT_ONLY}) é aplicada no endpoint.
 */
@Service
public class RevokeRoleAssignment {

    private final RoleAssignmentRepository roleAssignmentRepository;
    private final TenantAdminGuard tenantAdminGuard;

    public RevokeRoleAssignment(RoleAssignmentRepository roleAssignmentRepository,
                                TenantAdminGuard tenantAdminGuard) {
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.tenantAdminGuard = tenantAdminGuard;
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

        // Guarda do último administrador (lock) tem precedência: revogar o
        // último TENANT_ADMIN ativo (mesmo por si próprio) → 409.
        tenantAdminGuard.ensureCanRemoveTenantAdmin(tenantId, target);

        if (actor.equals(target)) {
            throw new AuthorizationDeniedException(
                "O ator não pode alterar as próprias concessões.");
        }

        roleAssignmentRepository.save(assignment.revoke(actor, Instant.now()));
    }
}
