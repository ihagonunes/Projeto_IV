package br.com.qsoft.governance.iam.application;

import java.util.UUID;

/**
 * Concessão ativa exibida em {@code /me/permissoes} / {@code /{id}/permissoes}
 * (SPEC-01 §7.3). {@code role} é a chave do papel de sistema.
 */
public record GrantView(
    UUID id,
    String role,
    String scope,
    UUID departmentId,
    String origin
) {
}
