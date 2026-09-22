package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.rbac.Permission;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Permissões efetivas de um usuário, no formato "RESOURCE:ACTION"
 * (usadas pelo frontend para habilitar/desabilitar componentes e ações).
 */
public record EffectivePermissionsResponse(UUID userId, Set<String> permissions) {

    public static EffectivePermissionsResponse from(UUID userId, Set<Permission> permissions) {
        Set<String> values = permissions.stream()
            .map(permission -> permission.resource().name() + ":" + permission.action().name())
            .collect(Collectors.toUnmodifiableSet());
        return new EffectivePermissionsResponse(userId, values);
    }
}
