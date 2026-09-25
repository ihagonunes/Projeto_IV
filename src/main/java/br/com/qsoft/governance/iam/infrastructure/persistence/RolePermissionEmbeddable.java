package br.com.qsoft.governance.iam.infrastructure.persistence;

import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.Objects;

/**
 * Permissão (recurso × ação) como @Embeddable da coleção de permissões do papel.
 */
@Embeddable
public class RolePermissionEmbeddable implements Serializable {

    @Enumerated(EnumType.STRING)
    @Column(name = "resource", nullable = false, length = 40)
    private Resource resource;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private Action action;

    protected RolePermissionEmbeddable() {
        // para JPA
    }

    public RolePermissionEmbeddable(Resource resource, Action action) {
        this.resource = resource;
        this.action = action;
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RolePermissionEmbeddable other)) {
            return false;
        }
        return resource == other.resource && action == other.action;
    }

    @Override
    public int hashCode() {
        return Objects.hash(resource, action);
    }
}
