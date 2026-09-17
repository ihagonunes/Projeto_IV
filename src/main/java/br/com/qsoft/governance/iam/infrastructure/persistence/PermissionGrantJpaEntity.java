package br.com.qsoft.governance.iam.infrastructure.persistence;

import br.com.qsoft.governance.iam.domain.permission.Action;
import br.com.qsoft.governance.iam.domain.permission.Resource;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/**
 * Persistência JPA da concessão de permissão na matriz global de permissões (POPROJ-52).
 * <p>
 * A matriz é a mesma para todos os tenants: não há {@code tenant_id} nem
 * {@code department_id}. O escopo de department é aplicado apenas na avaliação
 * (SPI-40). A unicidade é garantida pela constraint UNIQUE (role_level,
 * resource, action), que também serve de índice para a consulta eficiente
 * por (roleLevel, resource, action).
 */
@Entity
@Table(name = "permission_grant", uniqueConstraints = {
    @UniqueConstraint(name = "uq_permission_grant_role_resource_action",
        columnNames = {"role_level", "resource", "action"})
})
public class PermissionGrantJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_level", nullable = false, length = 20)
    private HierarchyLevel roleLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource", nullable = false, length = 40)
    private Resource resource;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private Action action;

    protected PermissionGrantJpaEntity() {
        // para JPA
    }

    public PermissionGrantJpaEntity(UUID id, HierarchyLevel roleLevel, Resource resource, Action action) {
        this.id = id;
        this.roleLevel = roleLevel;
        this.resource = resource;
        this.action = action;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public HierarchyLevel getRoleLevel() {
        return roleLevel;
    }

    public void setRoleLevel(HierarchyLevel roleLevel) {
        this.roleLevel = roleLevel;
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
}