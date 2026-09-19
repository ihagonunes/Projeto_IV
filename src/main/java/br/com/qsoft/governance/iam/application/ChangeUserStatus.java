package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.UserNotFoundException;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Objects;
import org.springframework.stereotype.Service;

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
