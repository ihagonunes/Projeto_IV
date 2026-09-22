package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.UserRepository;
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

    public User changeStatus(ChangeUserStatusCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        UserId targetUserId = Objects.requireNonNull(command.targetUserId(), "targetUserId");
        UserStatus newStatus = Objects.requireNonNull(command.newStatus(), "newStatus");

        if (newStatus == UserStatus.INACTIVE && command.executorId() != null && command.executorId().equals(targetUserId)) {
            throw new IllegalArgumentException("Você não pode desativar seu próprio usuário.");
        }

        User user = userRepository.findById(targetUserId)
            .orElseThrow(() -> new UserNotFoundException(targetUserId));

        if (!user.tenantId().equals(tenantId)) {
            throw new UserNotFoundException(targetUserId);
        }

        if (newStatus == UserStatus.ACTIVE) {
            user.activate();
        } else {
            user.deactivate();
        }

        return userRepository.save(user);
    }
}
