package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.UserNotFoundException;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ResendUserInvite {

    private final UserRepository userRepository;

    public ResendUserInvite(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User resend(ResendUserInviteCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        UserId targetUserId = Objects.requireNonNull(command.targetUserId(), "targetUserId");

        User user = userRepository.findById(targetUserId)
            .orElseThrow(() -> new UserNotFoundException(targetUserId));

        if (!user.tenantId().equals(tenantId)) {
            throw new UserNotFoundException(targetUserId);
        }

        return user;
    }
}
