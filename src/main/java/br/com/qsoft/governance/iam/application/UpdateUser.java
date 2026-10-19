package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.application.rbac.DominancePolicy;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.UserNotFoundException;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de edição do <b>Department</b> de um usuário existente (SPI-63).
 * <p>
 * O gate HTTP é {@code USER:MANAGE} em {@code BY_TARGET}; aqui ficam as guardas
 * finas (SPEC-01 §4): o ator não altera a si próprio, precisa dominar o alvo e o
 * novo departamento precisa estar no escopo do ator (AZ14). O rótulo não tem
 * efeito de autorização (ADR-36 item 9).
 */
@Service
public class UpdateUser {

    private final UserRepository userRepository;
    private final DominancePolicy dominancePolicy;

    public UpdateUser(UserRepository userRepository, DominancePolicy dominancePolicy) {
        this.userRepository = userRepository;
        this.dominancePolicy = dominancePolicy;
    }

    public User update(UpdateUserCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        UserId actor = Objects.requireNonNull(command.actorUserId(), "actorUserId");
        UserId target = Objects.requireNonNull(command.targetUserId(), "targetUserId");
        UUID newDepartmentId = command.departmentId();
        if (newDepartmentId == null) {
            throw new IllegalArgumentException("Informe departmentId.");
        }

        // findById é tenant-scoped (TenantContext): alvo de outro tenant → 404.
        User actorUser = userRepository.findById(actor)
            .orElseThrow(() -> new UserNotFoundException(actor));
        User targetUser = userRepository.findById(target)
            .orElseThrow(() -> new UserNotFoundException(target));
        TenantId tenantId = actorUser.tenantId();
        if (!tenantId.equals(targetUser.tenantId())) {
            throw new AuthorizationDeniedException(
                "Usuário não autorizado a alterar usuário de outro tenant.");
        }

        if (actor.equals(target)) {
            throw new AuthorizationDeniedException(
                "O ator não pode alterar o próprio usuário.");
        }
        if (!dominancePolicy.dominates(tenantId, actor, target)) {
            throw new AuthorizationDeniedException(
                "Ator não domina as concessões do usuário alvo.");
        }
        if (!dominancePolicy.coversDepartment(tenantId, actor, newDepartmentId)) {
            throw new AuthorizationDeniedException(
                "Departamento de destino fora do escopo do ator.");
        }

        targetUser.changeDepartment(newDepartmentId);
        return userRepository.save(targetUser);
    }
}
