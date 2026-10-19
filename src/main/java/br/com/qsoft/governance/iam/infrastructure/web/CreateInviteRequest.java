package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * Payload de emissão de convite (SPEC-01 §7.2). O convite carrega o papel
 * ({@code roleKey}); {@code departmentId} é obrigatório quando o papel é
 * departamental (validado no caso de uso). {@code hierarchyLevel} é rótulo
 * opcional (derivado do papel quando ausente).
 */
public record CreateInviteRequest(
    @NotBlank(message = "email é obrigatório")
    @Email(message = "email inválido")
    String email,

    @NotBlank(message = "roleKey é obrigatório")
    String roleKey,

    UUID departmentId,

    HierarchyLevel hierarchyLevel
) {
}
