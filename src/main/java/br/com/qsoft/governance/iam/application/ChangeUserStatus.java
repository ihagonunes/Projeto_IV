package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.UserNotFoundException;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de desativação e reativação de usuário (SPI-61).
 * <p>
 * A desativação é <b>lógica</b>: apenas o {@code status} do usuário muda para
 * INACTIVE — o registro e o histórico são preservados (nenhum delete). Um
 * usuário INACTIVE é impedido de autenticar ({@code AuthenticateUser}) e o
 * {@code TenantContextFilter} recusa requisições com token de usuário inativo;
 * a reativação restaura o acesso.
 * <p>
 * A AUTORIZAÇÃO (permissão granular USER:MANAGE) é aplicada no endpoint pelo
 * {@code PermissionInterceptor} via {@code @RequiresPermission}. Aqui permanece
 * o invariante de escopo: só altera usuários do MESMO tenant (ADR-03).
 */
@Service
public class ChangeUserStatus {

    private final UserRepository userRepository;

    public ChangeUserStatus(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User change(ChangeUserStatusCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        UserId actor = Objects.requireNonNull(command.actorUserId(), "actorUserId");
        UserId target = Objects.requireNonNull(command.targetUserId(), "targetUserId");
        UserStatus status = Objects.requireNonNull(command.status(), "status");

        User actorUser = userRepository.findById(actor)
            .orElseThrow(() -> new UserNotFoundException(actor));
        User targetUser = userRepository.findById(target)
            .orElseThrow(() -> new UserNotFoundException(target));

        // Escopo: só altera usuários do MESMO tenant (ADR-03).
        if (!actorUser.tenantId().equals(targetUser.tenantId())) {
            throw new AuthorizationDeniedException(
                "Usuário não autorizado a alterar status de usuário de outro tenant.");
        }

        // Guarda: um administrador não pode desativar a própria conta e se
        // trancar fora do sistema (o token passaria a ser recusado).
        if (status == UserStatus.INACTIVE && actor.equals(target)) {
            throw new IllegalArgumentException(
                "Não é permitido desativar o próprio usuário.");
        }

        if (status == UserStatus.ACTIVE) {
            targetUser.activate();
        } else {
            targetUser.deactivate();
        }
        return userRepository.save(targetUser);
    }
}
