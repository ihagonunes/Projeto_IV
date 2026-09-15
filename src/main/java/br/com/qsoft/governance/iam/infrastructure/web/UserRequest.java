package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Payload de criação de usuário.
 * O tenant é derivado do contexto autenticado (JWT) — nunca do cliente —
 * e as permissões não são definidas neste payload.
 */
public record UserRequest(
    @NotBlank(message = "email é obrigatório")
    String email,

    @NotBlank(message = "name é obrigatório")
    String name,

    @NotNull(message = "hierarchyLevel é obrigatório")
    HierarchyLevel hierarchyLevel,

    @NotBlank(message = "password é obrigatória")
    String password,

    UUID departmentId
) {
}
