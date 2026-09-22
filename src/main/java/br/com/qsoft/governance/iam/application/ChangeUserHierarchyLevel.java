package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.UserNotFoundException;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de alteração controlada do nível hierárquico de um usuário.
 *
 * A AUTORIZAÇÃO (permissão granular USER:MANAGE) é aplicada no endpoint pelo
 * {@code PermissionInterceptor} via {@code @RequiresPermission} (SPI-47). Aqui
 * permanece o invariante de escopo: só altera usuários do MESMO tenant (ADR-03).
 */
@Service
public class ChangeUserHierarchyLevel {

    private final UserRepository userRepository;

    public ChangeUserHierarchyLevel(UserRepository userRepository) {
        this.userRepository = userRepository;
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

        User actorUser = userRepository.findById(actor)
            .orElseThrow(() -> new UserNotFoundException(actor));
        User targetUser = userRepository.findById(target)
            .orElseThrow(() -> new UserNotFoundException(target));

        // Escopo: só altera usuários do MESMO tenant (ADR-03).
        if (!actorUser.tenantId().equals(targetUser.tenantId())) {
            throw new AuthorizationDeniedException(
                "Usuário não autorizado a alterar nível de usuário de outro tenant.");
        }

        targetUser.changeHierarchyLevel(newLevel);
        return userRepository.save(targetUser);
    }
}
