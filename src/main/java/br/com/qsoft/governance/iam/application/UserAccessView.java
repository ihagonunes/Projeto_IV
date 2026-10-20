package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.domain.rbac.Permission;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Concessões ativas e união das permissões de um usuário (SPEC-01 §7.3).
 * O front usa apenas para exibir menus; não é decisão de autorização.
 */
public record UserAccessView(
    UUID userId,
    List<GrantView> concessions,
    Set<Permission> permissions
) {
}
