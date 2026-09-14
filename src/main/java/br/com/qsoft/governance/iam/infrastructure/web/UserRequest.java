package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.api.model.Permission;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

/**
 * Payload de provisionamento (onboarding) de um usuário com senha.
 */
public record UserRequest(
    @NotNull(message = "tenantId é obrigatório")
    UUID tenantId,

    @NotBlank(message = "email é obrigatório")
    String email,

    @NotBlank(message = "name é obrigatório")
    String name,

    @NotNull(message = "hierarchyLevel é obrigatório")
    HierarchyLevel hierarchyLevel,

    @NotBlank(message = "password é obrigatória")
    String password,

    UUID departmentId,

    Set<Permission> permissions
) {
}
