package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.UserNotFoundException;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de edição de papel (HierarchyLevel) e Department de um usuário
 * existente (SPI-63).
 * <p>
 * A AUTORIZAÇÃO (permissão granular USER:MANAGE) é aplicada no endpoint pelo
 * {@code PermissionInterceptor} via {@code @RequiresPermission}. Aqui permanece
 * o invariante de escopo: só altera usuários do MESMO tenant (ADR-03).
 * <p>
 * A transferência organizacional completa (encerrar memberships antigas de
 * Department) depende de UserAreaMembership (ADR-05), ainda não modelado no IAM.
 */
@Service
public class UpdateUser {

    private final UserRepository userRepository;

    public UpdateUser(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User update(UpdateUserCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        UserId actor = Objects.requireNonNull(command.actorUserId(), "actorUserId");
        UserId target = Objects.requireNonNull(command.targetUserId(), "targetUserId");

        if (command.hierarchyLevel() == null && command.departmentId() == null) {
            throw new IllegalArgumentException("Informe hierarchyLevel e/ou departmentId.");
        }

        User actorUser = userRepository.findById(actor)
            .orElseThrow(() -> new UserNotFoundException(actor));
        User targetUser = userRepository.findById(target)
            .orElseThrow(() -> new UserNotFoundException(target));

        // Escopo: só altera usuários do MESMO tenant (ADR-03).
        if (!actorUser.tenantId().equals(targetUser.tenantId())) {
            throw new AuthorizationDeniedException(
                "Usuário não autorizado a alterar usuário de outro tenant.");
        }

        if (command.hierarchyLevel() != null) {
            targetUser.changeHierarchyLevel(command.hierarchyLevel());
        }
        if (command.departmentId() != null) {
            targetUser.changeDepartment(command.departmentId());
        }
        return userRepository.save(targetUser);
    }
}
