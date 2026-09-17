package br.com.qsoft.governance.iam.domain.permission;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import java.util.Objects;
import java.util.UUID;

/**
 * Concessão de permissão na matriz (roleLevel, resource, action) (POPROJ-52).
 * <p>
 * A matriz é GLOBAL: não possui {@code tenant_id} nem {@code department_id},
 * pois é a mesma para todos os tenants (regra de negócio, não dado por tenant).
 * O escopo de departamento é aplicado na avaliação (SPI-40), comparando o
 * departmentId do usuário com o departmentId do recurso acessado — não faz
 * parte da tupla armazenada nesta entidade.
 * <p>
 * O domínio não depende de JPA/Spring (ADR-31).
 */
public final class PermissionGrant {

    private final UUID id;
    private final HierarchyLevel roleLevel;
    private final Resource resource;
    private final Action action;

    public PermissionGrant(UUID id, HierarchyLevel roleLevel, Resource resource, Action action) {
        this.id = Objects.requireNonNull(id, "id");
        this.roleLevel = Objects.requireNonNull(roleLevel, "roleLevel");
        this.resource = Objects.requireNonNull(resource, "resource");
        this.action = Objects.requireNonNull(action, "action");
    }

    public UUID id() {
        return id;
    }

    public HierarchyLevel roleLevel() {
        return roleLevel;
    }

    public Resource resource() {
        return resource;
    }

    public Action action() {
        return action;
    }
}