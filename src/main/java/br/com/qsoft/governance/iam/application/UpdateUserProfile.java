package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.UserNotFoundException;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class UpdateUserProfile {

    private final UserRepository userRepository;

    public UpdateUserProfile(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User updateProfile(UpdateUserProfileCommand command) {
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

        if (command.departmentId() != null) {
            user.updateDepartment(command.departmentId());
        }

        if (command.hierarchyLevel() != null) {
            user.changeHierarchyLevel(command.hierarchyLevel());
        }

        if (command.isAdmin() != null) {
            user.updateAdminPermissions(command.isAdmin());
        }

        return userRepository.save(user);
    }
}
