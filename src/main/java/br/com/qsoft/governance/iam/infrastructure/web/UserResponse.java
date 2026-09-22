package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record UserResponse(
    String id,
    String tenantId,
    String email,
    @JsonProperty("nome") String name,
    @JsonProperty("nivelHierarquico") HierarchyLevel hierarchyLevel,
    @JsonProperty("departamento") String departmentId,
    @JsonProperty("permissaoAdmin") String adminPermission,
    @JsonProperty("status") UserStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.id().value().toString(),
            user.tenantId().value().toString(),
            user.email(),
            user.name(),
            user.hierarchyLevel(),
            user.departmentId() != null ? user.departmentId().toString() : null,
            user.isAdmin() ? "ADMIN" : "COMUM",
            user.status(),
            user.createdAt(),
            user.updatedAt()
        );
    }
}
