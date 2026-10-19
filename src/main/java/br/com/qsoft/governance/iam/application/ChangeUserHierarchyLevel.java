package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.application.rbac.DominancePolicy;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.UserNotFoundException;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de alteração do rótulo hierárquico de um usuário (SPI-47/SPI-63).
 * <p>
 * O rótulo é <b>organizacional</b>: não tem efeito de autorização (ADR-36 item 9).
 * Guardas: o ator não altera a si próprio (403) e precisa dominar o alvo (403).
 */
@Service
public class ChangeUserHierarchyLevel {

    private final UserRepository userRepository;
    private final DominancePolicy dominancePolicy;

    public ChangeUserHierarchyLevel(UserRepository userRepository,
                                    DominancePolicy dominancePolicy) {
        this.userRepository = userRepository;
        this.dominancePolicy = dominancePolicy;
    }

    public User change(ChangeUserHierarchyLevelCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        UserId actor = Objects.requireNonNull(command.actorUserId(), "actorUserId");
        UserId target = Objects.requireNonNull(command.targetUserId(), "targetUserId");
        HierarchyLevel newLevel = command.newLevel();
        if (newLevel == null) {
            throw new IllegalArgumentException("newLevel é obrigatório.");
        }

        // findById é tenant-scoped (TenantContext): alvo de outro tenant → 404.
        User actorUser = userRepository.findById(actor)
            .orElseThrow(() -> new UserNotFoundException(actor));
        User targetUser = userRepository.findById(target)
            .orElseThrow(() -> new UserNotFoundException(target));
        TenantId tenantId = actorUser.tenantId();
        if (!tenantId.equals(targetUser.tenantId())) {
            throw new AuthorizationDeniedException(
                "Usuário não autorizado a alterar nível de usuário de outro tenant.");
        }

        if (actor.equals(target)) {
            throw new AuthorizationDeniedException(
                "O ator não pode alterar o próprio rótulo.");
        }
        if (!dominancePolicy.dominates(tenantId, actor, target)) {
            throw new AuthorizationDeniedException(
                "Ator não domina as concessões do usuário alvo.");
        }

        targetUser.changeHierarchyLevel(newLevel);
        return userRepository.save(targetUser);
    }
}
