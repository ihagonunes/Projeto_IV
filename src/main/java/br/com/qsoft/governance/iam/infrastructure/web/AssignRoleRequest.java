package br.com.qsoft.governance.iam.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * Payload de atribuição direta de uma concessão (SPEC-01 §7.4).
 */
public record AssignRoleRequest(
    @NotBlank(message = "roleKey é obrigatório")
    String roleKey,

    UUID departmentId
) {
}
