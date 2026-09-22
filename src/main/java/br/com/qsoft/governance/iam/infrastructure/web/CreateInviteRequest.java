package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Payload de emissão de convite.
 */
public record CreateInviteRequest(
    @NotBlank(message = "email é obrigatório")
    @Email(message = "email inválido")
    String email,

    @NotNull(message = "hierarchyLevel é obrigatório")
    HierarchyLevel hierarchyLevel,

    UUID departmentId
) {
}
