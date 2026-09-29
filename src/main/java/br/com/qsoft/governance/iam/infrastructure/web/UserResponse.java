package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.user.User;
import java.util.UUID;

/**
 * Representação pública de um usuário.
 */
public record UserResponse(
    UUID id,
    UUID tenantId,
    String email,
    String name,
    String hierarchyLevel,
    UUID departmentId,
    String status
) {

    public static UserResponse from(User user) {
        return new UserResponse(
            user.id().value(),
            user.tenantId().value(),
            user.email(),
            user.name(),
            user.hierarchyLevel().name(),
            user.departmentId(),
            user.status().name()
        );
    }
}
