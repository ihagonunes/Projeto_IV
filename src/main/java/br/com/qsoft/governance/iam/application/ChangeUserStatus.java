package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.application.rbac.DominancePolicy;
import br.com.qsoft.governance.iam.application.rbac.TenantAdminGuard;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.UserNotFoundException;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de desativação e reativação de usuário (SPI-61).
 * <p>
 * A desativação é <b>lógica</b> (nenhum delete). Guardas (SPEC-01 §4):
 * o ator não altera a si próprio (403), precisa dominar o alvo (403) e não se
 * desativa o último {@code TENANT_ADMIN} ativo do tenant (409, com lock).
 */
@Service
public class ChangeUserStatus {

    private final UserRepository userRepository;
    private final DominancePolicy dominancePolicy;
    private final TenantAdminGuard tenantAdminGuard;

    public ChangeUserStatus(UserRepository userRepository,
                            DominancePolicy dominancePolicy,
                            TenantAdminGuard tenantAdminGuard) {
        this.userRepository = userRepository;
        this.dominancePolicy = dominancePolicy;
        this.tenantAdminGuard = tenantAdminGuard;
    }

    public User change(ChangeUserStatusCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        UserId actor = Objects.requireNonNull(command.actorUserId(), "actorUserId");
        UserId target = Objects.requireNonNull(command.targetUserId(), "targetUserId");
        UserStatus status = Objects.requireNonNull(command.status(), "status");

        // findById é tenant-scoped (TenantContext): alvo de outro tenant → 404.
        User actorUser = userRepository.findById(actor)
            .orElseThrow(() -> new UserNotFoundException(actor));
        User targetUser = userRepository.findById(target)
            .orElseThrow(() -> new UserNotFoundException(target));
        TenantId tenantId = actorUser.tenantId();
        if (!tenantId.equals(targetUser.tenantId())) {
            throw new AuthorizationDeniedException(
                "Usuário não autorizado a alterar status de usuário de outro tenant.");
        }

        if (status == UserStatus.INACTIVE) {
            // Precedência: não deixar o tenant sem administrador (409).
            tenantAdminGuard.ensureCanRemoveTenantAdmin(tenantId, target);
        }
        if (actor.equals(target)) {
            throw new AuthorizationDeniedException(
                "O ator não pode alterar o próprio status.");
        }
        if (!dominancePolicy.dominates(tenantId, actor, target)) {
            throw new AuthorizationDeniedException(
                "Ator não domina as concessões do usuário alvo.");
        }

        if (status == UserStatus.ACTIVE) {
            targetUser.activate();
        } else {
            targetUser.deactivate();
        }
        return userRepository.save(targetUser);
    }
}
