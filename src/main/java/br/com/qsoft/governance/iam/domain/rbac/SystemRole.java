package br.com.qsoft.governance.iam.domain.rbac;

import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.ScopeType;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Papéis de sistema com escopo fixo (ADR-36). Substitui a antiga
 * {@code PermissionMatrix} por nível hierárquico.
 * <p>
 * O nome do papel (persistido em {@code iam_roles.name}) é a chave do papel de
 * sistema. As permissões usam <b>match exato</b> recurso × ação: {@code MANAGE}
 * não implica as demais ações.
 */
public enum SystemRole {

    COLLABORATOR(ScopeType.SELF, 1, collaboratorPermissions()),
    LEADER(ScopeType.DEPARTMENT, 2, leaderPermissions()),
    MANAGER(ScopeType.DEPARTMENT, 3, managerPermissions()),
    EXECUTIVE(ScopeType.TENANT, 4, executivePermissions()),
    /** Administrativo, fora da escada de postos. Sem CHAT ou leitura analítica. */
    TENANT_ADMIN(ScopeType.TENANT, 100, tenantAdminPermissions());

    private final ScopeType scope;
    private final int rank;
    private final Set<Permission> permissions;

    SystemRole(ScopeType scope, int rank, Set<Permission> permissions) {
        this.scope = scope;
        this.rank = rank;
        this.permissions = permissions;
    }

    public ScopeType scope() {
        return scope;
    }

    public int rank() {
        return rank;
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    /** Resolve o papel de sistema pelo nome persistido. Vazio se não for de sistema. */
    public static Optional<SystemRole> fromKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String normalized = key.trim();
        for (SystemRole role : values()) {
            if (role.name().equals(normalized)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }

    /** Apenas COLLABORATOR e LEADER podem ser concedidos por não-administradores (ADR-37). */
    public boolean isDelegableByNonAdmins() {
        return this == COLLABORATOR || this == LEADER;
    }

    private static Set<Permission> collaboratorPermissions() {
        return Set.of(
            p(Resource.CHAT, Action.CREATE),
            p(Resource.CHAT, Action.READ),
            p(Resource.KNOWLEDGE_DOCUMENT, Action.READ),
            p(Resource.USAGE, Action.READ),
            p(Resource.MODEL, Action.READ));
    }

    private static Set<Permission> leaderPermissions() {
        Set<Permission> permissions = new HashSet<>(collaboratorPermissions());
        permissions.add(p(Resource.USER, Action.READ));
        permissions.add(p(Resource.GOVERNANCE_SCORE, Action.READ));
        return Set.copyOf(permissions);
    }

    private static Set<Permission> managerPermissions() {
        Set<Permission> permissions = new HashSet<>(leaderPermissions());
        permissions.add(p(Resource.USER, Action.MANAGE));
        permissions.add(p(Resource.INVITE, Action.MANAGE));
        permissions.add(p(Resource.ROLE, Action.READ));
        permissions.add(p(Resource.DEPARTMENT, Action.READ));
        permissions.add(p(Resource.PROJECT, Action.MANAGE));
        permissions.add(p(Resource.AI_TOOL, Action.READ));
        permissions.add(p(Resource.SPENDING_ALERT, Action.MANAGE));
        return Set.copyOf(permissions);
    }

    private static Set<Permission> executivePermissions() {
        Set<Permission> permissions = new HashSet<>(managerPermissions());
        permissions.add(p(Resource.TENANT, Action.READ));
        permissions.add(p(Resource.DEPARTMENT_BUDGET, Action.MANAGE));
        permissions.add(p(Resource.SPENDING_BUDGET, Action.MANAGE));
        permissions.remove(p(Resource.SPENDING_ALERT, Action.MANAGE));
        return Set.copyOf(permissions);
    }

    private static Set<Permission> tenantAdminPermissions() {
        Set<Permission> permissions = new HashSet<>();
        permissions.add(p(Resource.TENANT, Action.READ));
        permissions.add(p(Resource.TENANT, Action.MANAGE));
        permissions.add(p(Resource.ROLE, Action.READ));
        permissions.add(p(Resource.ROLE, Action.MANAGE));
        permissions.add(p(Resource.USER, Action.READ));
        permissions.add(p(Resource.USER, Action.MANAGE));
        permissions.add(p(Resource.INVITE, Action.READ));
        permissions.add(p(Resource.INVITE, Action.MANAGE));
        permissions.add(p(Resource.DEPARTMENT, Action.READ));
        permissions.add(p(Resource.DEPARTMENT, Action.MANAGE));
        permissions.add(p(Resource.AI_TOOL, Action.READ));
        permissions.add(p(Resource.AI_TOOL, Action.MANAGE));
        permissions.add(p(Resource.MODEL, Action.READ));
        permissions.add(p(Resource.MODEL, Action.MANAGE));
        permissions.add(p(Resource.KNOWLEDGE_DOCUMENT, Action.READ));
        permissions.add(p(Resource.KNOWLEDGE_DOCUMENT, Action.MANAGE));
        permissions.add(p(Resource.LICENSE, Action.MANAGE));
        permissions.add(p(Resource.TRAINING, Action.MANAGE));
        return Set.copyOf(permissions);
    }

    private static Permission p(Resource resource, Action action) {
        return Permission.of(resource, action);
    }
}
